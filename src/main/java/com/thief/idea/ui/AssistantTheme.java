package com.thief.idea.ui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * 伪装主题：AI 编码助手风格的配色、字体与小控件。
 * <p>
 * 所有颜色都按"亮色 / 暗色"成对定义，跟随 IDE 主题；不要在业务代码里写死颜色，
 * 否则暗色主题下会出现看不清的文字。
 **/
public final class AssistantTheme {

    private AssistantTheme() {
    }

    /**
     * 聊天区主文字（与 IDE 前景保持一致的可读性）
     **/
    public static final Color TEXT = new JBColor(new Color(0x24292F), new Color(0xD6D6D6));

    /**
     * 次要文字：时间戳、说明、占位符等
     **/
    public static final Color MUTED = new JBColor(new Color(0x8B949E), new Color(0x8E9299));

    /**
     * 代码卡片背景 / 头部背景 / 描边
     **/
    public static final Color CARD_BG = new JBColor(new Color(0xFFFFFF), new Color(0x1E1F22));
    public static final Color CARD_HEADER_BG = new JBColor(new Color(0xF2F4F7), new Color(0x26282E));
    public static final Color CARD_BORDER = new JBColor(new Color(0xDCDFE4), new Color(0x33363C));

    /**
     * diff 行背景：新增（绿）/ 删除（红）
     **/
    public static final Color ADD_BG = new JBColor(new Color(0xE6F4EA), new Color(0x1E3A26));
    public static final Color ADD_BG_HOVER = new JBColor(new Color(0xD3EBDD), new Color(0x254C31));
    public static final Color DEL_BG = new JBColor(new Color(0xFBEAE8), new Color(0x402426));

    /**
     * 代码文字 / 行号
     **/
    public static final Color CODE_TEXT = new JBColor(new Color(0x37474F), new Color(0xC8CCD4));

    /**
     * 行内代码块（chip）背景与文字
     **/
    public static final Color CHIP_BG = new JBColor(new Color(0xE7EAEE), new Color(0x2E3138));
    public static final Color CHIP_TEXT = new JBColor(new Color(0x3A4048), new Color(0xC6CAD1));

    /**
     * 单行 shell 命令块的提示符 `$`：用强调色，一眼能看出这是"跑命令"而不是"改代码"。
     * 块本身的底色/描边沿用 diff 卡片那套（CARD_BG / CARD_BORDER），两者头部才一致
     **/
    public static final Color SHELL_PROMPT = new JBColor(new Color(0x2E9E6B), new Color(0x4CC48D));

    /**
     * 输入框背景 / 描边 / 发送按钮
     **/
    public static final Color INPUT_BG = new JBColor(new Color(0xFFFFFF), new Color(0x1E1F22));
    public static final Color INPUT_BORDER = new JBColor(new Color(0xD5D9DE), new Color(0x35383E));
    public static final Color ACCENT = new JBColor(new Color(0x2E9E6B), new Color(0x35A574));
    public static final Color ACCENT_HOVER = new JBColor(new Color(0x278A5C), new Color(0x2E9165));
    public static final Color ACCENT_FG = new Color(0xFFFFFF);

    /**
     * 图标按钮悬停底色
     **/
    public static final Color HOVER_BG = new JBColor(new Color(0xE9ECF0), new Color(0x32353B));

    /**
     * 品牌图标底色
     **/
    public static final Color BRAND_BG = new JBColor(new Color(0x2E9E6B), new Color(0x35A574));

    public static Color panelBackground() {
        return UIUtil.getPanelBackground();
    }

    /**
     * 界面字体（跟随 IDE 默认字体，保证中文不出现方框）
     **/
    public static Font uiFont(int size) {
        return UIUtil.getFontWithFallback(UIUtil.getLabelFont()).deriveFont(Font.PLAIN, (float) size);
    }

    public static Font bold(Font font) {
        return font.deriveFont(Font.BOLD);
    }

    public static Font mono(int size) {
        return UIUtil.getFontWithFallback(new Font(Font.MONOSPACED, Font.PLAIN, size));
    }

    /**
     * 用于代码卡片的紧凑等宽字体
     **/
    public static Font monoSmall(int baseSize) {
        return mono(Math.max(10, baseSize - 2));
    }

    /**
     * 把组件包一层 BorderLayout，使其在 BoxLayout 中横向撑满。
     * 必须显式放开最大宽度，否则 BoxLayout 只会给到组件的首选宽度。
     **/
    public static JComponent stretch(JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(component, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * 透明、不可编辑、自动换行的文本面板（用于渲染一段助手回复）。
     * 只读不等于不可选：文本可以选中复制，配置见 {@link #makeSelectable}
     **/
    public static JTextPane textPane() {
        JTextPane pane = new JTextPane();
        pane.setOpaque(false);
        pane.setEditable(false);
        pane.setBorder(JBUI.Borders.empty());
        makeSelectable(pane);
        return pane;
    }

    /**
     * 把只读文本组件配置成"可选中、可复制"：
     * <ul>
     *   <li><b>必须可获取焦点</b>——不可聚焦的文本组件无法建立选区，这就是"正文选不中"的根因，
     *   不能为了"点上去没反应"而设 {@code setFocusable(false)}；</li>
     *   <li>不画插入符：只读回复不该出现闪烁光标（否则看着像可编辑的输入框），
     *   覆盖的是只画插入符的 {@code paint(Graphics)}；选区高亮走
     *   {@code Highlighter.HighlightPainter#paint} 那个重载，不受影响；</li>
     *   <li>补一个"复制 / 全选"右键菜单（{@link JTextPane} 默认没有右键菜单）。</li>
     * </ul>
     **/
    public static void makeSelectable(JTextComponent component) {
        component.setFocusable(true);
        component.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        component.setCaret(new DefaultCaret() {
            @Override
            public void paint(Graphics g) {
                // 只读文本不画插入符
            }
        });
        component.setComponentPopupMenu(textMenu(component));
    }

    /**
     * 只读文本的右键菜单：复制 / 全选
     **/
    private static JPopupMenu textMenu(JTextComponent component) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copy = new JMenuItem("复制");
        copy.addActionListener(e -> component.copy());
        JMenuItem selectAll = new JMenuItem("全选");
        selectAll.addActionListener(e -> component.selectAll());
        menu.add(copy);
        menu.add(selectAll);
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                copy.setEnabled(component.getSelectionStart() != component.getSelectionEnd());
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        return menu;
    }

    /**
     * 单行只读文本（代码行、命令行、小标题）：透明无边框、可选中复制。
     * <p>
     * 与 {@link JTextPane} 的区别是<b>不按可用宽度折行</b>——首选宽度只由文本自身决定，
     * 所以能直接替换原来的 {@link JLabel} 而不改变卡片的行高；宽度不够时和 JLabel 一样裁剪
     **/
    public static class SelectableText extends JTextField {
        public SelectableText(String text, Font font, Color color) {
            super(text);
            setEditable(false);
            setOpaque(false);
            setBorder(JBUI.Borders.empty());
            setMargin(new Insets(0, 0, 0, 0));
            setFont(font);
            setForeground(color);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            makeSelectable(this);
        }

        /**
         * 首选尺寸按字体度量算，与原来的 {@link JLabel} 完全一致——{@code JTextField} 默认会在
         * 文本宽度上加几个像素（给插入符留位），换组件后那一行文字会整体位移 1~2px，
         * 卡片头部和 shell 块的对齐就跟着变
         **/
        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            if (metrics == null) {
                return super.getPreferredSize();
            }
            return new Dimension(metrics.stringWidth(getText()), metrics.getHeight());
        }
    }

    /**
     * 圆角容器：内部子组件会被裁剪到圆角内（否则不透明的子组件会盖住圆角）
     **/
    public static class RoundedPanel extends JPanel {
        private final int radius;
        private Color fill;
        private Color border;

        public RoundedPanel(LayoutManager layout, int radius, Color fill, Color border) {
            super(layout);
            this.radius = JBUI.scale(radius);
            this.fill = fill;
            this.border = border;
            setOpaque(false);
        }

        public void setFill(Color fill) {
            this.fill = fill;
        }

        public void setBorderColor(Color border) {
            this.border = border;
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1f, getHeight() - 1f, radius, radius);
            g2.clip(shape);
            if (fill != null) {
                g2.setColor(fill);
                g2.fill(shape);
            }
            super.paint(g2);
            if (border != null) {
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(shape);
            }
            g2.dispose();
        }
    }

    /**
     * 无边框的图标/字形按钮：悬停时出现淡色圆角底，模拟 IDE 里的工具栏图标按钮。
     * 也可以给定常驻底色（例如 diff 卡片上的 Apply）
     **/
    public static class GlyphButton extends JButton {
        private final int corner;
        private Color baseBackground;
        private Color hoverBackground = HOVER_BG;

        public GlyphButton(String glyph, Font font) {
            super(glyph);
            this.corner = JBUI.scale(4);
            setFont(font);
            setForeground(MUTED);
            init();
        }

        public GlyphButton(Icon icon) {
            super(icon);
            this.corner = JBUI.scale(4);
            init();
        }

        private void init() {
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setBorder(JBUI.Borders.empty(2));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
        }

        /**
         * 常驻底色为 null 时只有悬停才出现底色
         **/
        public GlyphButton baseBackground(Color color) {
            this.baseBackground = color;
            return this;
        }

        public GlyphButton hoverBackground(Color color) {
            this.hoverBackground = color;
            return this;
        }

        public GlyphButton colors(Color foreground, Color hover) {
            setForeground(foreground);
            this.hoverBackground = hover;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color fill = getModel().isRollover() && isEnabled() ? hoverBackground : baseBackground;
            if (fill != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), corner, corner);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
