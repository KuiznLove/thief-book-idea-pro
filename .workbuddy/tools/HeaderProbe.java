import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.thief.idea.ui.AssistantIcons;
import com.thief.idea.ui.AssistantTheme;
import com.thief.idea.ui.ChatInputBar;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 顶栏/底栏对齐探针：用真实组件（{@link AssistantTheme.GlyphButton}、{@link AssistantIcons}）
 * 按指定缩放因子复刻 MainUi.initHeaderBar() 与 ChatInputBar 工具行的两栏结构，然后：
 * <ol>
 *   <li>打印每个控件的 bounds 与垂直中心线（相对顶栏左上角，单位=设备像素）；</li>
 *   <li>打印每个图标"墨迹包围盒中心"相对"图标框中心"的偏移——这是"图标画得偏不偏"的直接证据；</li>
 *   <li>把整条栏画成 PNG（设备像素 1:1），便于后续用 InkProfile 复核。</li>
 * </ol>
 * 用法：HeaderProbe &lt;scale&gt; [宽度(逻辑px)=302] [输出.png]
 * 例：  HeaderProbe 1.5 302 顶栏15x.png
 */
public class HeaderProbe {

    private static float SCALE = 1.5f;
    /** 字体是否也按 HiDPI 放大（离线复刻时 JBUI.Fonts 不会自动跟随，用它来模拟真实 IDE） */
    private static float FONT_SCALE = 1f;

    static Font uiFont(int size, boolean bold) {
        Font font = AssistantTheme.uiFont(size);
        if (bold) {
            font = font.deriveFont(Font.BOLD);
        }
        return FONT_SCALE == 1f ? font : font.deriveFont(size * FONT_SCALE);
    }

    static class Result {
        final String name;
        final Rectangle bounds;
        double inkOffsetY = Double.NaN;

        Result(String name, Rectangle bounds) {
            this.name = name;
            this.bounds = bounds;
        }
    }

    public static void main(String[] args) throws Exception {
        SCALE = args.length > 0 ? Float.parseFloat(args[0]) : 1.5f;
        FONT_SCALE = Float.parseFloat(System.getProperty("probe.fontScale", "1"));
        int logicalWidth = args.length > 1 ? Integer.parseInt(args[1]) : 302;
        String out = args.length > 2 ? args[2] : "headerprobe.png";

        // 注意：JBUIScale 的缩放只在"重算"时生效——只调 setSystemScaleFactor 是没用的
        // （sysScale() 会变，但 scale() 用的仍是预计算值），必须再调一次 setUserScaleFactorForTest。
        JBUIScale.setSystemScaleFactor(SCALE);
        JBUIScale.setUserScaleFactorForTest(SCALE);

        JComponent header = header("CodePilot", "会话 1", "177 / 873", true);

        int width = JBUI.scale(logicalWidth);
        header.setSize(width, header.getPreferredSize().height);
        layoutAll(header);

        System.out.println("=== 缩放 " + SCALE + "x  逻辑宽 " + logicalWidth + " -> 设备宽 " + width + " ===");
        System.out.println("顶栏设备高度=" + header.getHeight() + "（pref=" + header.getPreferredSize().height + "）");
        for (Result r : collect(header)) {
            System.out.printf("  %-14s x=%4d..%-4d y=%3d..%-3d (高%3d)  垂直中心=%6.1f%s%n",
                    r.name, r.bounds.x, r.bounds.x + r.bounds.width - 1,
                    r.bounds.y, r.bounds.y + r.bounds.height - 1, r.bounds.height,
                    r.bounds.y + r.bounds.height / 2.0,
                    Double.isNaN(r.inkOffsetY) ? "" : String.format("   图标墨迹中心相对图标框中心 = %+.1f", r.inkOffsetY));
        }

        BufferedImage image = new BufferedImage(header.getWidth(), header.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        header.paint(g);
        g.dispose();
        ImageIO.write(image, "png", new File(out));
        System.out.println("written: " + new File(out).getAbsolutePath());

        // 底部输入栏的工具行（@ # 图片 / 模型 / 发送）同样要"所有子控件共线"
        ChatInputBar input = new ChatInputBar(new ChatInputBar.Listener() {
            @Override
            public void onSend(String text) {
            }

            @Override
            public void onPreviousPage() {
            }

            @Override
            public void onNextPage() {
            }

            @Override
            public void onModelChanged(String model) {
            }

            @Override
            public void onRenameAssistant() {
            }
        }, "Seed-Code");
        input.setSize(width, input.getPreferredSize().height);
        layoutAll(input);
        System.out.println();
        System.out.println("=== 输入栏（整体高 " + input.getHeight() + "）===");
        for (Result r : collect(input)) {
            System.out.printf("  %-22s x=%4d..%-4d y=%3d..%-3d (高%3d)  垂直中心=%6.1f%n",
                    r.name, r.bounds.x, r.bounds.x + r.bounds.width - 1,
                    r.bounds.y, r.bounds.y + r.bounds.height - 1, r.bounds.height,
                    r.bounds.y + r.bounds.height / 2.0);
        }
    }

    private static JComponent header(String brandText, String sessionText, String pageText, boolean sidebarOn) {
        JPanel bar = new JPanel(new AssistantTheme.RowLayout(JBUI.scale(8)));
        bar.setOpaque(true);
        bar.setBackground(AssistantTheme.panelBackground());
        bar.setBorder(JBUI.Borders.empty(7, JBUI.scale(14), 4, JBUI.scale(12)));

        JLabel brand = new JLabel(brandText);
        brand.setIcon(AssistantIcons.brand(JBUI.scale(17)));
        brand.setIconTextGap(JBUI.scale(7));
        brand.setFont(uiFont(12, true));
        brand.setForeground(AssistantTheme.TEXT);

        JButton sidebar = null;
        if (sidebarOn) {
            sidebar = headerButton(AssistantIcons.sidebar(JBUI.scale(15), AssistantTheme.MUTED, true));
        }

        JButton session = new AssistantTheme.GlyphButton(sessionText, uiFont(11, false));
        session.setIcon(AssistantIcons.chevronDown(JBUI.scale(13), AssistantTheme.MUTED));
        session.setHorizontalTextPosition(SwingConstants.LEFT);
        session.setIconTextGap(JBUI.scale(3));
        session.setBorder(JBUI.Borders.empty(2, JBUI.scale(6), 2, JBUI.scale(4)));

        JLabel page = new JLabel(pageText);
        page.setFont(uiFont(11, false));
        page.setForeground(AssistantTheme.MUTED);
        page.setBorder(JBUI.Borders.empty(0, 0, 0, JBUI.scale(6)));

        JButton boss = new JButton(" ");
        boss.setPreferredSize(new Dimension(5, 5));

        JButton prev = headerButton(AssistantIcons.chevronLeft(JBUI.scale(15), AssistantTheme.MUTED));
        JButton next = headerButton(AssistantIcons.chevronRight(JBUI.scale(15), AssistantTheme.MUTED));
        JButton speaker = headerButton(AssistantIcons.speaker(JBUI.scale(15), AssistantTheme.MUTED, false));

        AssistantTheme.RowLayout.gapBefore(brand, JBUI.scale(8));
        bar.add(brand);
        if (sidebar != null) {
            bar.add(sidebar);
        }
        bar.add(session);
        bar.add(AssistantTheme.RowLayout.spring());
        AssistantTheme.RowLayout.gapBefore(page, 0);
        bar.add(page);
        for (JComponent item : new JComponent[]{prev, next, speaker, boss}) {
            AssistantTheme.RowLayout.gapBefore(item, JBUI.scale(2));
            bar.add(item);
        }
        return bar;
    }

    private static JButton headerButton(Icon icon) {
        AssistantTheme.GlyphButton button = new AssistantTheme.GlyphButton(icon);
        button.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(22)));
        return button;
    }

    private static void layoutAll(Container c) {
        c.doLayout();
        for (Component k : c.getComponents()) {
            if (k instanceof Container) {
                layoutAll((Container) k);
            }
        }
    }

    /** 收集所有直接可视控件的 bounds（相对顶栏），并量测图标墨迹偏移 */
    private static java.util.List<Result> collect(JComponent bar) {
        java.util.List<Result> list = new java.util.ArrayList<>();
        walk(bar, bar, list);
        return list;
    }

    private static void walk(Container parent, JComponent origin, java.util.List<Result> list) {
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            String name = child.getClass().getSimpleName();
            if (child instanceof JLabel) {
                name = "label:" + shorten(((JLabel) child).getText());
            } else if (child instanceof AbstractButton && ((AbstractButton) child).getText() != null
                    && !((AbstractButton) child).getText().isEmpty()) {
                name = "btn:" + shorten(((AbstractButton) child).getText());
            }
            Point p = SwingUtilities.convertPoint(child.getParent(), child.getLocation(), origin);
            Result r = new Result(name + "[" + child.getPreferredSize().height + "]", new Rectangle(p, child.getSize()));
            Icon icon = (child instanceof JLabel) ? ((JLabel) child).getIcon()
                    : (child instanceof AbstractButton) ? ((AbstractButton) child).getIcon() : null;
            if (icon != null) {
                r.inkOffsetY = inkOffsetY(icon);
            }
            list.add(r);
            if (child instanceof Container) {
                walk((Container) child, origin, list);
            }
        }
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() > 8 ? t.substring(0, 8) + "…" : t;
    }

    /**
     * 把图标画进一张比它大的画布，量出墨迹包围盒的中心相对图标框中心的偏移。
     * 图标本身画得偏（例如 SVG 光栅化时竖直方向多算了 1~2px），在截图上就表现为"这一行图标不齐"。
     */
    private static double inkOffsetY(Icon icon) {
        int w = icon.getIconWidth() + 8;
        int h = icon.getIconHeight() + 8;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        icon.paintIcon(new JLabel(), g, 4, 4);
        g.dispose();
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) > 40) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (bottom < 0) {
            return Double.NaN;
        }
        double inkCenter = (top + bottom) / 2.0;
        double boxCenter = 4 + (icon.getIconHeight() - 1) / 2.0;
        return inkCenter - boxCenter;
    }
}
