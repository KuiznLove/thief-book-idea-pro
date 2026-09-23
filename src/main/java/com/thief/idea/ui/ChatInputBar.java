package com.thief.idea.ui;

import com.intellij.util.ui.JBUI;
import com.thief.idea.disguise.DisguiseContent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 底部的"对话框"：外观完全照搬 AI 编码助手的输入区（占位提示 + @ / # / 图片按钮 + 模型选择 + 发送按钮），
 * 实际用途是翻页与跳页：
 * <ul>
 *   <li>回车 / 点击发送按钮 → 下一页（"继续"）</li>
 *   <li>输入框内 ↑ / ↓ → 上一页 / 下一页</li>
 *   <li>输入纯数字回车 → 跳到第 N 页</li>
 *   <li>输入 /next /prev /play /stop 等命令 → 对应的阅读操作</li>
 * </ul>
 **/
public final class ChatInputBar extends JPanel {

    /**
     * 回调：真正的翻页/跳页动作由 MainUi 执行
     **/
    public interface Listener {
        /**
         * 用户按下回车或点击发送按钮
         **/
        void onSend(String text);

        void onPreviousPage();

        void onNextPage();

        void onModelChanged(String model);

        void onRenameAssistant();
    }

    private static final String PLACEHOLDER = "\"↑↓\" 切换历史输入，\"Shift+Enter\" 换行";

    private final Listener listener;
    private final PlaceholderArea input;
    private final JLabel toast;
    private final Timer toastTimer;
    private final AssistantTheme.GlyphButton modelButton;

    /**
     * 设置页里填的"自定义模型名"（纯伪装）：非空时作为额外一项出现在模型列表末尾
     **/
    private String customModel = "";

    public ChatInputBar(Listener listener, String modelName) {
        super(new BorderLayout(0, JBUI.scale(4)));
        this.listener = listener;
        setOpaque(false);
        setBorder(JBUI.Borders.empty(4, JBUI.scale(14), JBUI.scale(10), JBUI.scale(14)));

        toast = new JLabel();
        toast.setFont(AssistantTheme.uiFont(11));
        toast.setForeground(AssistantTheme.MUTED);
        toast.setBorder(JBUI.Borders.empty(0, JBUI.scale(4), 0, 0));
        toast.setVisible(false);
        add(toast, BorderLayout.NORTH);

        input = new PlaceholderArea();
        input.setFont(AssistantTheme.uiFont(12));
        input.setForeground(AssistantTheme.TEXT);
        input.setCaretColor(AssistantTheme.TEXT);
        input.setOpaque(false);
        input.setBorder(JBUI.Borders.empty(JBUI.scale(7), JBUI.scale(9), JBUI.scale(2), JBUI.scale(9)));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e);
            }
        });

        modelButton = new AssistantTheme.GlyphButton(modelName, AssistantTheme.uiFont(11));
        modelButton.setIcon(AssistantIcons.chevronDown(JBUI.scale(14), AssistantTheme.MUTED));
        modelButton.setHorizontalTextPosition(SwingConstants.LEFT);
        modelButton.setIconTextGap(JBUI.scale(3));
        modelButton.setBorder(JBUI.Borders.empty(2, JBUI.scale(6), 2, JBUI.scale(4)));
        modelButton.setToolTipText("选择模型");
        modelButton.addActionListener(e -> showMenu(modelButton, modelItems()));

        AssistantTheme.RoundedPanel box = new AssistantTheme.RoundedPanel(new BorderLayout(),
                10, AssistantTheme.INPUT_BG, AssistantTheme.INPUT_BORDER);
        box.add(input, BorderLayout.CENTER);
        box.add(createToolbar(), BorderLayout.SOUTH);
        add(box, BorderLayout.CENTER);

        toastTimer = new Timer(2400, e -> toast.setVisible(false));
        toastTimer.setRepeats(false);
    }

    /**
     * 输入区下方的一行：左侧伪装按钮，右侧模型选择与发送
     **/
    private JComponent createToolbar() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.empty(0, JBUI.scale(6), JBUI.scale(5), JBUI.scale(6)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0));
        left.setOpaque(false);
        AssistantTheme.GlyphButton at = glyphButton("@", "添加代码上下文");
        at.addActionListener(e -> showMenu(at,
                itemMenu("Files & Folders", "Git Diff", "Terminal Output", "Codebase Index")));
        AssistantTheme.GlyphButton hash = glyphButton("#", "引用当前文件");
        hash.addActionListener(e -> showMenu(hash,
                itemMenu("Current File", "Selection", "Open Editors", "Workspace")));
        AssistantTheme.GlyphButton picture = iconButton(AssistantIcons.image(JBUI.scale(15), AssistantTheme.MUTED),
                "插入图片");
        picture.addActionListener(e -> showMenu(picture,
                itemMenu("Screenshot", "Clipboard Image", "From File...")));
        left.add(at);
        left.add(hash);
        left.add(picture);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0));
        right.setOpaque(false);
        right.add(modelButton);
        right.add(new SendButton());

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private AssistantTheme.GlyphButton glyphButton(String glyph, String tooltip) {
        AssistantTheme.GlyphButton button = new AssistantTheme.GlyphButton(glyph,
                AssistantTheme.uiFont(14).deriveFont(Font.BOLD));
        button.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(22)));
        button.setToolTipText(tooltip);
        return button;
    }

    private AssistantTheme.GlyphButton iconButton(Icon icon, String tooltip) {
        AssistantTheme.GlyphButton button = new AssistantTheme.GlyphButton(icon);
        button.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(22)));
        button.setToolTipText(tooltip);
        return button;
    }

    /**
     * 输入框按键：回车发送、Shift+Enter 换行、↑↓ 翻页、Esc 清空
     **/
    private void handleKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (e.isShiftDown()) {
                return;
            }
            e.consume();
            send();
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            e.consume();
            input.setText("");
            return;
        }
        // 输入多行内容时 ↑↓ 交还给光标移动，避免影响正常编辑
        if (!input.getText().contains("\n")) {
            if (e.getKeyCode() == KeyEvent.VK_UP) {
                e.consume();
                listener.onPreviousPage();
            } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                e.consume();
                listener.onNextPage();
            }
        }
    }

    private void send() {
        String text = input.getText() == null ? "" : input.getText().trim();
        input.setText("");
        if (listener != null) {
            listener.onSend(text);
        }
    }

    /**
     * 底部提示行：显示伪装反馈（已复制到剪贴板 / 已应用等），几秒后自动消失
     **/
    public void showToast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        toast.setText(message);
        toast.setVisible(true);
        toastTimer.restart();
    }

    public void setModel(String model) {
        if (model != null && !model.isEmpty()) {
            modelButton.setText(model);
        }
    }

    /**
     * 输入框本体，供工具窗口指定"激活时优先聚焦的组件"。
     * <p>
     * 阅读区正文现在是可选中文本（可聚焦的文本组件才能用鼠标建立选区），于是它也会参与
     * 焦点遍历、并排在输入框前面：工具窗口激活时焦点会落在正文上，用户直接按 ↑ / ↓ 或
     * 打字都没有反应（正文不可编辑）。把输入框声明为优先聚焦组件就回到原来的行为
     **/
    public JComponent inputComponent() {
        return input;
    }

    /**
     * 设置页里填的自定义模型名（纯伪装）。传空串表示没有自定义模型。
     * 与预设模型重名时不重复添加，避免列表里出现两个一样的条目
     **/
    public void setCustomModel(String custom) {
        this.customModel = custom == null ? "" : custom.trim();
    }

    public void focusInput() {
        input.requestFocusInWindow();
    }

    /**
     * 伪装的下拉菜单：显示在按钮上方（底部输入区更自然）
     **/
    private void showMenu(JComponent invoker, JPopupMenu menu) {
        menu.show(invoker, 0, -menu.getPreferredSize().height - JBUI.scale(2));
    }

    private JPopupMenu itemMenu(String... items) {
        JPopupMenu menu = new JPopupMenu();
        for (String item : items) {
            JMenuItem menuItem = new JMenuItem(item);
            menuItem.addActionListener(e -> showToast(DisguiseContent.TOAST_ADDED + "：" + item));
            menu.add(menuItem);
        }
        return menu;
    }

    /**
     * 可选的模型列表 = 内置预设 + 设置页里填的自定义模型（去重，自定义项排在最后）
     **/
    private List<String> modelChoices() {
        List<String> choices = new ArrayList<>(Arrays.asList(DisguiseContent.MODELS));
        if (!customModel.isEmpty() && !choices.contains(customModel)) {
            choices.add(customModel);
        }
        return choices;
    }

    private JPopupMenu modelItems() {
        JPopupMenu menu = new JPopupMenu();
        String current = modelButton.getText();
        for (String model : modelChoices()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(model, model.equals(current));
            item.addActionListener(e -> {
                setModel(model);
                if (listener != null) {
                    listener.onModelChanged(model);
                }
                showToast("已切换到 " + model);
            });
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem rename = new JMenuItem("重命名助手…");
        rename.addActionListener(e -> {
            if (listener != null) {
                listener.onRenameAssistant();
            }
        });
        menu.add(rename);
        return menu;
    }

    /**
     * 带占位提示的输入区：占位文案模仿 AI 助手的输入框
     **/
    private final class PlaceholderArea extends JTextArea {
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!getText().isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(AssistantTheme.MUTED);
            g2.setFont(getFont());
            Insets insets = getInsets();
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(PLACEHOLDER, insets.left, insets.top + metrics.getAscent());
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            return new Dimension(preferred.width, Math.max(preferred.height, JBUI.scale(20)));
        }
    }

    /**
     * 发送按钮：绿色圆角方块 + 向上箭头（点击 = 下一页）
     **/
    private final class SendButton extends JButton {
        SendButton() {
            super(AssistantIcons.arrowUp(JBUI.scale(15), AssistantTheme.ACCENT_FG));
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(JBUI.scale(30), JBUI.scale(24)));
            setToolTipText("发送");
            addActionListener(e -> send());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getModel().isRollover() ? AssistantTheme.ACCENT_HOVER : AssistantTheme.ACCENT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), JBUI.scale(7), JBUI.scale(7));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
