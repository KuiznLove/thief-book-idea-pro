package com.thief.idea.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SVGLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伪装界面用到的图标。
 * <p>
 * 形状取自开源图标集 <b>Lucide</b>（ISC License，原件与许可见 {@code resources/icons/lucide/}）。
 * 不使用平台内置图标集，避免版本差异导致编译期或运行期缺图标；也不依赖字体里的特殊符号
 * （"↑"、"@" 这类字符在部分系统字体里会变成方框）。
 * <p>
 * <b>品牌标记与文件角标仍是手绘</b>（{@link #brand(int)} / {@link #fileBadge}）：素材里没有
 * "品牌图形"与"文字角标"的对应物。
 * <p>
 * <b>为什么自己光栅化</b>：这里的图标颜色是运行时传入的（悬停变色、朗读激活、侧栏展开状态），
 * 而 2023.3 平台没有"给插件图标染任意色"的公开 API（{@code IconManager.colorize} 需要平台自己的
 * 图标解析链路，把 {@code IconLoader} 加载的图标染一下反而会丢掉它的分辨率信息），官方文档给的
 * 也只是 {@code _dark} / {@code @2x} 这类静态变体。所以本类直接读 SVG 源码、在<b>矢量层面</b>替换颜色，
 * 再按<b>绘制时的缩放倍数</b>光栅化（见 {@link SvgIcon}）——既保留原有 API（16 个调用点不用改），
 * 也不必为明暗主题各存一份资源：颜色来自 {@link AssistantTheme} 的 JBColor，主题切换自然跟随。
 **/
public final class AssistantIcons {

    private static final Logger LOG = Logger.getInstance(AssistantIcons.class);

    /**
     * Lucide 图标所在目录（resources 根下）
     **/
    private static final String SVG_DIR = "/icons/lucide/";

    /**
     * Lucide 资源统一是 24×24 视框，光栅化时的 scale 是相对它的倍数
     **/
    private static final float INTRINSIC_SIZE = 24f;

    /**
     * SVG 源码里被替换的颜色（Lucide 的描边色）
     **/
    private static final String SOURCE_COLOR = "#000000";

    /**
     * 光栅化结果缓存：同一个图标会被界面反复请求（多个按钮、每次翻页重绘）。
     * key = 图标名 + 像素尺寸 + 颜色（像素尺寸按绘制倍数变化，故同一逻辑尺寸下可能有几份）
     **/
    private static final Map<String, Image> RASTERS = new ConcurrentHashMap<>();

    /**
     * SVG 源码缓存（按图标名），只在首次读取
     **/
    private static final Map<String, byte[]> SOURCES = new ConcurrentHashMap<>();

    /**
     * 渲染失败的 key，避免每帧重试并刷日志
     **/
    private static final Set<String> BROKEN = ConcurrentHashMap.newKeySet();

    private static final byte[] MISSING = new byte[0];

    private AssistantIcons() {
    }

    /**
     * 在 16x16 网格上绘制的图标回调（手绘图标用）
     **/
    public interface Painter {
        void paint(Graphics2D g, int size, Color color);
    }

    public static Icon icon(int size, Color color, Painter painter) {
        return new VectorIcon(size, color, painter);
    }

    /**
     * 加载 Lucide 图标并染成指定颜色
     **/
    public static Icon svg(String name, int size, Color color) {
        return new SvgIcon(name, size, color);
    }

    /**
     * 由 SVG 资源渲染的图标。
     * <p>
     * <b>HiDPI 的关键</b>：位图必须按"绘制时的缩放倍数"光栅化，并以<b>逻辑尺寸</b>落笔，这样位图与
     * 设备像素正好 1:1——否则 Swing 会把小位图插值放大，图标就是糊的（v0.3.4 踩过：16px 的位图在
     * 2x 屏上被拉成 32 设备像素）。
     * <ul>
     *   <li>{@code t} = 绘制时 Graphics 的缩放倍数（JRE HiDPI 模式下即设备倍数，如 2.0；否则为 1.0）；</li>
     *   <li>光栅化尺寸 = {@code size × t} 像素；</li>
     *   <li>落笔尺寸 = {@code size} 逻辑单位 → 设备上占 {@code size × t} 像素，与位图 1:1。</li>
     * </ul>
     * 上式在两种 HiDPI 模式下都成立，所以不需要判断当前用的是哪一种。
     * <p>
     * 颜色在矢量层面替换（把 SVG 源码里的 {@code #000000} 换成目标色）：抗锯齿由渲染器按最终颜色计算，
     * 边缘比"先画黑、再按 alpha 染色"更干净，也省掉一次全图合成。
     **/
    private static final class SvgIcon implements Icon {

        private final String name;
        private final int size;
        private final Color color;

        SvgIcon(String name, int size, Color color) {
            this.name = name;
            this.size = size;
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Image raster = raster(Math.max(1, Math.round(size * graphicsScale(g))));
            if (raster != null) {
                g.drawImage(raster, x, y, size, size, null);
            }
        }

        /**
         * 绘制时 Graphics 的缩放倍数（1.0 = 无缩放）
         **/
        private static float graphicsScale(Graphics g) {
            if (g instanceof Graphics2D) {
                double scale = Math.abs(((Graphics2D) g).getTransform().getScaleX());
                if (scale > 0.01) {
                    return (float) scale;
                }
            }
            return 1f;
        }

        /**
         * 取指定像素尺寸的位图（带缓存）
         **/
        private Image raster(int pixels) {
            String key = name + '@' + pixels + '#' + Integer.toHexString(color.getRGB());
            Image cached = RASTERS.get(key);
            if (cached != null) {
                return cached;
            }
            if (BROKEN.contains(key)) {
                return null;
            }
            Image rendered = render(pixels);
            if (rendered == null) {
                BROKEN.add(key);
            } else {
                RASTERS.put(key, rendered);
            }
            return rendered;
        }

        /**
         * 取 SVG 源码 → 换色 → 按目标像素尺寸光栅化
         **/
        private Image render(int pixels) {
            byte[] svg = source();
            if (svg == MISSING) {
                return null;
            }
            String text = new String(svg, StandardCharsets.UTF_8)
                    .replace(SOURCE_COLOR, String.format("#%06X", color.getRGB() & 0xFFFFFF));
            try (InputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
                return SVGLoader.load(in, pixels / INTRINSIC_SIZE);
            } catch (IOException | RuntimeException e) {
                LOG.warn("图标渲染失败：" + name + " @" + pixels + "px - " + e);
                return null;
            }
        }

        /**
         * 读取 SVG 源码（缓存；读不到时返回 {@link #MISSING} 并只报一次）
         **/
        private byte[] source() {
            return SOURCES.computeIfAbsent(name, key -> {
                try (InputStream in = AssistantIcons.class.getResourceAsStream(SVG_DIR + key + ".svg")) {
                    if (in == null) {
                        LOG.warn("图标资源缺失：" + SVG_DIR + key + ".svg");
                        return MISSING;
                    }
                    return in.readAllBytes();
                } catch (IOException e) {
                    LOG.warn("图标资源读取失败：" + SVG_DIR + key + ".svg - " + e);
                    return MISSING;
                }
            });
        }
    }

    private static final class VectorIcon implements Icon {
        private final int size;
        private final Color color;
        private final Painter painter;

        VectorIcon(int size, Color color, Painter painter) {
            this.size = size;
            this.color = color;
            this.painter = painter;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(Math.max(1.15f, size / 13f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            painter.paint(g2, size, color);
            g2.dispose();
        }
    }

    /**
     * 四角星（品牌标记）
     **/
    public static Icon brand(int size) {
        return icon(size, Color.WHITE, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(AssistantTheme.BRAND_BG);
            g.fill(new RoundRectangle2D.Float(u, u, 14 * u, 14 * u, 4.5f * u, 4.5f * u));
            g.setColor(c);
            g.fill(star(8 * u, 8 * u, 5f * u, 1.6f * u));
        });
    }

    private static Path2D star(float cx, float cy, float outer, float inner) {
        Path2D path = new Path2D.Float();
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(-90 + i * 45);
            float r = i % 2 == 0 ? outer : inner;
            float x = (float) (cx + r * Math.cos(angle));
            float y = (float) (cy + r * Math.sin(angle));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    /**
     * 向上箭头（发送 / 继续）
     **/
    public static Icon arrowUp(int size, Color color) {
        return svg("arrow-up", size, color);
    }

    public static Icon copy(int size, Color color) {
        return svg("copy", size, color);
    }

    public static Icon download(int size, Color color) {
        return svg("download", size, color);
    }

    public static Icon refresh(int size, Color color) {
        return svg("refresh-cw", size, color);
    }

    public static Icon check(int size, Color color) {
        return svg("check", size, color);
    }

    /**
     * 实心播放三角：shell 代码块右上角 "Run" 按钮用（与 diff 卡片的 "Apply" 对勾区分）
     **/
    public static Icon play(int size, Color color) {
        return svg("play", size, color);
    }

    public static Icon chevronDown(int size, Color color) {
        return svg("chevron-down", size, color);
    }

    public static Icon chevronLeft(int size, Color color) {
        return svg("chevron-left", size, color);
    }

    public static Icon chevronRight(int size, Color color) {
        return svg("chevron-right", size, color);
    }

    /**
     * 喇叭图标：朗读中用两道声波（volume-2），未朗读用一道（volume-1）
     **/
    public static Icon speaker(int size, Color color, boolean active) {
        return svg(active ? "volume-2" : "volume-1", size, color);
    }

    /**
     * 图片图标（"插入图片"按钮）
     **/
    public static Icon image(int size, Color color) {
        return svg("image", size, color);
    }

    /**
     * 侧栏开关图标：展开中显示"可收起"（panel-left-close），已收起显示"可展开"（panel-left），
     * 与 IDE 里"收起/展开侧边栏"按钮的图标语义一致
     **/
    public static Icon sidebar(int size, Color color, boolean expanded) {
        return svg(expanded ? "panel-left-close" : "panel-left", size, color);
    }

    /**
     * 文件类型角标：圆角色块 + 两三个字母（PY / YAML…）
     **/
    public static Icon fileBadge(String text, Color background, Color foreground, int size) {
        return icon(size, foreground, (g, s, c) -> {
            g.setColor(background);
            g.fill(new RoundRectangle2D.Float(0, 0, s, s, 3.5f, 3.5f));
            Font font = AssistantTheme.uiFont(Math.max(7, Math.round(s * 0.42f))).deriveFont(Font.BOLD);
            g.setFont(font);
            g.setColor(c);
            FontMetrics metrics = g.getFontMetrics();
            float x = (s - metrics.stringWidth(text)) / 2f;
            float y = (s - metrics.getHeight()) / 2f + metrics.getAscent();
            g.drawString(text, x, y);
        });
    }
}
