import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.ImageUtil;
import com.thief.idea.ui.AssistantIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图标清晰度检查（HiDPI）。
 * <p>
 * 用法：IconHiDpiCheck &lt;sysScale&gt; [图标名] [逻辑尺寸]
 * 例：  IconHiDpiCheck 2 copy 16
 * <p>
 * <b>为什么需要它</b>：图标"糊"在缩略图上看不出来，肉眼判断不可靠（v0.3.4 就是这么放过问题的）。
 * 本工具把图标画到画布上，再<b>与"按目标像素尺寸直接渲染的参考图"逐像素比对</b>——参考图由同一个
 * 渲染器（平台 JSVG）渲染，也就是"理想情况长什么样"。
 * <ul>
 *   <li>差异 ≈ 0 → 位图与设备像素 1:1 落笔，清晰；</li>
 *   <li>差异明显 → 位图被插值缩放（先小尺寸光栅化再放大），就是"糊"；</li>
 *   <li>墨水尺寸不对 → 逻辑尺寸/设备倍数算错了（图标会偏大或偏小）。</li>
 * </ul>
 * <p>
 * 分别测两种绘制方式，对应 IntelliJ 的两种 HiDPI 模式：
 * <ul>
 *   <li>{@code plain}：Graphics 无缩放——JRE HiDPI 关闭、靠"用户缩放倍数"放大界面的模式；</li>
 *   <li>{@code scaled}：Graphics 已带设备倍数缩放——JRE HiDPI 模式。</li>
 * </ul>
 * 正确的实现（按绘制时的缩放倍数决定光栅化分辨率）在<b>两种模式下都该是 0 差异</b>。
 */
public class IconHiDpiCheck {

    /**
     * 参考色（假装是 AssistantTheme 的次要文字色）
     **/
    private static final Color INK = new Color(0x9AA3AD);

    public static void main(String[] args) throws Exception {
        float sysScale = args.length > 0 ? Float.parseFloat(args[0]) : 2f;
        String name = args.length > 1 ? args[1] : "copy";
        int logical = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        JBUIScale.setSystemScaleFactor(sysScale);

        System.out.println("[环境] 设定 sysScale=" + sysScale + "   JBUIScale.sysScale()=" + JBUIScale.sysScale());
        // 参考图必须和被测图标同色，否则比出来的是颜色差不是清晰度
        byte[] svg = new String(readSvg(name), java.nio.charset.StandardCharsets.UTF_8)
                .replace("#000000", String.format("#%06X", INK.getRGB() & 0xFFFFFF))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("[参考] SVGLoader.load(scale=16/24) → " + dims(SVGLoader.load(new ByteArrayInputStream(svg), 16 / 24f))
                + "   (scale=32/24) → " + dims(SVGLoader.load(new ByteArrayInputStream(svg), 32 / 24f)));

        Icon icon = build(name, logical);
        System.out.println("[被测] AssistantIcons." + name + "(" + logical + ") → getIconWidth=" + icon.getIconWidth()
                + (icon.getIconWidth() == logical ? "  (逻辑尺寸正确)" : "  ← 逻辑尺寸不对！"));

        report("plain       ", icon, 1.0, svg, logical);
        report("scaled x" + sysScale + "  ", icon, sysScale, svg, logical);
    }

    /**
     * 图标名 → {@code AssistantIcons} 里的入口方法
     **/
    private static Icon build(String name, int size) {
        switch (name) {
            case "arrow-up":
                return AssistantIcons.arrowUp(size, INK);
            case "copy":
                return AssistantIcons.copy(size, INK);
            case "download":
                return AssistantIcons.download(size, INK);
            case "refresh-cw":
                return AssistantIcons.refresh(size, INK);
            case "check":
                return AssistantIcons.check(size, INK);
            case "play":
                return AssistantIcons.play(size, INK);
            case "chevron-down":
                return AssistantIcons.chevronDown(size, INK);
            case "chevron-left":
                return AssistantIcons.chevronLeft(size, INK);
            case "chevron-right":
                return AssistantIcons.chevronRight(size, INK);
            case "volume-2":
                return AssistantIcons.speaker(size, INK, true);
            case "volume-1":
                return AssistantIcons.speaker(size, INK, false);
            case "image":
                return AssistantIcons.image(size, INK);
            case "panel-left-close":
                return AssistantIcons.sidebar(size, INK, true);
            case "panel-left":
                return AssistantIcons.sidebar(size, INK, false);
            default:
                throw new IllegalArgumentException("未登记的图标名：" + name);
        }
    }

    /**
     * 把图标画到画布上，与"该模式下应有的目标像素尺寸"参考图比对
     **/
    private static void report(String label, Icon icon, double graphicsScale, byte[] svg, int logical) throws IOException {
        int expected = Math.max(1, (int) Math.round(logical * graphicsScale));
        BufferedImage reference = ImageUtil.toBufferedImage(SVGLoader.load(new ByteArrayInputStream(svg), expected / 24f));

        int span = expected + 8;
        BufferedImage canvas = new BufferedImage(span, span, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        // 与 IDE 一样开插值：这正是"糊"的来源（位图被重新采样）
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.scale(graphicsScale, graphicsScale);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        int[] box = inkBox(canvas);
        int soft = softPixels(canvas);
        int[] diff = diff(canvas, reference, expected);
        boolean sizeOk = box[0] >= 0 && Math.abs((box[2] - box[0] + 1) - inkOf(reference)) <= 2;

        System.out.println(String.format("%s 期望 %-3dpx | 墨水 %-9s 尺寸%s | 不透明 %-5d 半透明 %-4d | 差异 %d/%d px (%.1f%%) → %s",
                label, expected,
                box[0] < 0 ? "(无)" : (box[2] - box[0] + 1) + "x" + (box[3] - box[1] + 1),
                sizeOk ? "对 " : "错!",
                box[4], soft, diff[0], diff[1], diff[0] * 100.0 / diff[1],
                diff[0] <= diff[1] * 0.02 ? "清晰" : "糊"));
    }

    private static int inkOf(BufferedImage image) {
        int[] box = inkBox(image);
        return box[0] < 0 ? 0 : box[2] - box[0] + 1;
    }

    /**
     * 墨水包围盒：[x0,y0,x1,y1,不透明像素数]，无墨水时 x0=-1
     **/
    private static int[] inkBox(BufferedImage image) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = -1;
        int y1 = -1;
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                    opaque++;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        return x0 == Integer.MAX_VALUE ? new int[]{-1, -1, -1, -1, 0} : new int[]{x0, y0, x1, y1, opaque};
    }

    /**
     * 半透明像素（0 < alpha < 240）数量：边缘被插值"抹开"时这个数字会明显变大
     **/
    private static int softPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int a = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (a > 0 && a < 240) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 与参考图的差异：返回 [差异像素数, 总像素数]（最大通道差 > 24 记为一处不同）
     **/
    private static int[] diff(BufferedImage canvas, BufferedImage reference, int expected) {
        int bad = 0;
        int total = 0;
        for (int y = 0; y < expected && y < reference.getHeight(); y++) {
            for (int x = 0; x < expected && x < reference.getWidth(); x++) {
                total++;
                int p = canvas.getRGB(x, y);
                int q = reference.getRGB(x, y);
                int d = 0;
                for (int shift = 0; shift <= 24; shift += 8) {
                    d = Math.max(d, Math.abs(((p >>> shift) & 0xFF) - ((q >>> shift) & 0xFF)));
                }
                if (d > 24) {
                    bad++;
                }
            }
        }
        return new int[]{bad, total};
    }

    private static byte[] readSvg(String name) throws IOException {
        try (InputStream in = IconHiDpiCheck.class.getResourceAsStream("/icons/lucide/" + name + ".svg")) {
            if (in == null) {
                throw new IOException("找不到 /icons/lucide/" + name + ".svg（classpath 是否包含 src/main/resources？）");
            }
            return in.readAllBytes();
        }
    }

    private static String dims(Image image) {
        return image == null ? "(null)" : image.getWidth(null) + "x" + image.getHeight(null);
    }
}
