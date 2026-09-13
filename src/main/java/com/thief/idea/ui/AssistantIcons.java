package com.thief.idea.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 伪装界面用到的矢量图标。
 * <p>
 * 全部用 Graphics2D 手绘，不依赖字体中的特殊符号（"@"、"↑" 这类字符在部分系统字体里会变成方框），
 * 也不依赖平台内置图标集，避免版本差异导致编译期或运行期缺图标。
 **/
public final class AssistantIcons {

    private AssistantIcons() {
    }

    /**
     * 在 16x16 网格上绘制的图标回调
     **/
    public interface Painter {
        void paint(Graphics2D g, int size, Color color);
    }

    public static Icon icon(int size, Color color, Painter painter) {
        return new VectorIcon(size, color, painter);
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
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            g.draw(new java.awt.geom.Line2D.Float(8 * u, 13 * u, 8 * u, 4 * u));
            Path2D head = new Path2D.Float();
            head.moveTo(4.6f * u, 7.4f * u);
            head.lineTo(8 * u, 4 * u);
            head.lineTo(11.4f * u, 7.4f * u);
            g.draw(head);
        });
    }

    public static Icon copy(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            Path2D back = new Path2D.Float();
            back.moveTo(5.5f * u, 10.5f * u);
            back.lineTo(5.5f * u, 2.5f * u);
            back.lineTo(13.5f * u, 2.5f * u);
            back.lineTo(13.5f * u, 10.5f * u);
            g.draw(back);
            g.draw(new RoundRectangle2D.Float(2.5f * u, 5.5f * u, 8 * u, 8 * u, 1.6f * u, 1.6f * u));
        });
    }

    public static Icon download(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            g.draw(new java.awt.geom.Line2D.Float(8 * u, 2.5f * u, 8 * u, 10.5f * u));
            Path2D head = new Path2D.Float();
            head.moveTo(4.8f * u, 7.3f * u);
            head.lineTo(8 * u, 10.6f * u);
            head.lineTo(11.2f * u, 7.3f * u);
            g.draw(head);
            Path2D tray = new Path2D.Float();
            tray.moveTo(3.2f * u, 11.8f * u);
            tray.lineTo(3.2f * u, 13.6f * u);
            tray.lineTo(12.8f * u, 13.6f * u);
            tray.lineTo(12.8f * u, 11.8f * u);
            g.draw(tray);
        });
    }

    public static Icon refresh(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            g.draw(new Arc2D.Float(3 * u, 3 * u, 10 * u, 10 * u, 70, 280, Arc2D.OPEN));
            Path2D head = new Path2D.Float();
            head.moveTo(11.4f * u, 2.6f * u);
            head.lineTo(12.4f * u, 5.6f * u);
            head.lineTo(9.4f * u, 5.2f * u);
            head.closePath();
            g.fill(head);
        });
    }

    public static Icon check(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            Path2D path = new Path2D.Float();
            path.moveTo(3.4f * u, 8.6f * u);
            path.lineTo(6.6f * u, 11.6f * u);
            path.lineTo(12.6f * u, 4.4f * u);
            g.draw(path);
        });
    }

    /**
     * 实心播放三角：shell 代码块右上角 "Run" 按钮用（与 diff 卡片的 "Apply" 对勾区分开）
     **/
    public static Icon play(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            Path2D path = new Path2D.Float();
            path.moveTo(4.6f * u, 3.2f * u);
            path.lineTo(12.4f * u, 8f * u);
            path.lineTo(4.6f * u, 12.8f * u);
            path.closePath();
            g.fill(path);
        });
    }

    public static Icon chevronDown(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            Path2D path = new Path2D.Float();
            path.moveTo(4.4f * u, 6.4f * u);
            path.lineTo(8 * u, 10 * u);
            path.lineTo(11.6f * u, 6.4f * u);
            g.draw(path);
        });
    }

    public static Icon chevronLeft(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            Path2D path = new Path2D.Float();
            path.moveTo(9.6f * u, 4.4f * u);
            path.lineTo(6 * u, 8 * u);
            path.lineTo(9.6f * u, 11.6f * u);
            g.draw(path);
        });
    }

    public static Icon chevronRight(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            Path2D path = new Path2D.Float();
            path.moveTo(6.4f * u, 4.4f * u);
            path.lineTo(10 * u, 8 * u);
            path.lineTo(6.4f * u, 11.6f * u);
            g.draw(path);
        });
    }

    /**
     * 喇叭图标：朗读中带两道声波，未朗读只有一道
     **/
    public static Icon speaker(int size, Color color, boolean active) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            g.fill(new RoundRectangle2D.Float(2.2f * u, 6 * u, 3.4f * u, 4 * u, 1f * u, 1f * u));
            Path2D cone = new Path2D.Float();
            cone.moveTo(5.6f * u, 6 * u);
            cone.lineTo(9 * u, 3 * u);
            cone.lineTo(9 * u, 13 * u);
            cone.lineTo(5.6f * u, 10 * u);
            cone.closePath();
            g.fill(cone);
            g.draw(new Arc2D.Float(6.6f * u, 4.6f * u, 7 * u, 6.8f * u, -55, 110, Arc2D.OPEN));
            if (active) {
                g.draw(new Arc2D.Float(7.4f * u, 2.6f * u, 11 * u, 10.8f * u, -55, 110, Arc2D.OPEN));
            }
        });
    }

    /**
     * 图片图标（"插入图片"按钮）
     **/
    public static Icon image(int size, Color color) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            g.draw(new RoundRectangle2D.Float(2.2f * u, 3.4f * u, 11.6f * u, 9.2f * u, 2f * u, 2f * u));
            g.fill(new java.awt.geom.Ellipse2D.Float(4.8f * u, 5.5f * u, 2f * u, 2f * u));
            Path2D hill = new Path2D.Float();
            hill.moveTo(3.6f * u, 11.6f * u);
            hill.lineTo(7 * u, 8.2f * u);
            hill.lineTo(9.2f * u, 10.3f * u);
            hill.lineTo(11f * u, 8.5f * u);
            hill.lineTo(13f * u, 10.6f * u);
            g.draw(hill);
        });
    }

    /**
     * 侧栏开关图标：外框 + 左列。
     * expanded=true 表示侧栏正展开（左列填实），false 表示已收起（左列只留一条描边），
     * 与 IDE 里"收起/展开侧边栏"的按钮样式一致。
     **/
    public static Icon sidebar(int size, Color color, boolean expanded) {
        return icon(size, color, (g, s, c) -> {
            float u = s / 16f;
            g.setColor(c);
            g.draw(new RoundRectangle2D.Float(2.2f * u, 3.4f * u, 11.6f * u, 9.2f * u, 2.2f * u, 2.2f * u));
            if (expanded) {
                g.fill(new RoundRectangle2D.Float(3.5f * u, 4.7f * u, 3f * u, 6.6f * u, 1.1f * u, 1.1f * u));
            } else {
                g.draw(new java.awt.geom.Line2D.Float(5.2f * u, 4.7f * u, 5.2f * u, 11.3f * u));
            }
        });
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
