package com.thief.idea;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.thief.idea.book.BookPager;
import com.thief.idea.book.BookSource;
import com.thief.idea.disguise.DisguiseContent;
import com.thief.idea.tts.TtsEngines;
import com.thief.idea.tts.TtsService;
import com.thief.idea.ui.AssistantIcons;
import com.thief.idea.ui.AssistantPageView;
import com.thief.idea.ui.AssistantTheme;
import com.thief.idea.ui.ChatInputBar;
import com.thief.idea.util.EpubUtil;
import com.thief.idea.util.HotkeyUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 阅读工具窗口：外观完全伪装成一个 AI 编码助手面板（回复 + 代码卡片 + 底部输入框）。
 * <p>
 * 正文被渲染成"助手的回复"，页与页之间靠翻页切换；操作入口：
 * Ctrl+1 上一页、Ctrl+2 下一页、Ctrl+4 语音朗读、Ctrl+3 老板键（窗口伪装成 Terminal），
 * 底部输入框回车 / 点发送按钮 = 下一页，输入框内 ↑↓ = 翻页，输入页码回车 = 跳页。
 **/
public class MainUi implements ToolWindowFactory, DumbAware {

    /**
     * 工具窗口 id：伪装成 AI 助手，注册见 plugin.xml
     **/
    public static final String TOOL_WINDOW_ID = "ai-assistant";

    /**
     * 各项目工具窗口对应的 MainUi 实例，供全局老板键 action 查找并触发隐藏
     **/
    private static final Map<Project, MainUi> instances = new ConcurrentHashMap<>();

    /**
     * 根据项目获取 MainUi 实例，项目已销毁或窗口未创建时返回 null
     **/
    public static MainUi getInstance(Project project) {
        if (project == null) {
            return null;
        }
        MainUi mainUi = instances.get(project);
        if (mainUi != null && project.isDisposed()) {
            instances.remove(project);
            return null;
        }
        return mainUi;
    }

    private static final Logger LOG = Logger.getInstance(MainUi.class);

    private PersistentState persistentState = PersistentState.getInstance();

    /**
     * 读取文件路径
     **/
    private String bookFile = persistentState.getBookPathText();

    /**
     * 当前书的读取来源（epub 解包 / 编码检测），切书时重建
     **/
    private BookSource bookSource;

    /**
     * 当前书的分页引擎（页码 / 指针缓存 / 按页读取），切书时重建。
     * 手动翻页与朗读取页共用它，复合操作在引擎内串行，不会交错推进页码
     **/
    private BookPager pager;

    /**
     * epub 目录面板（左侧），仅在 epub 且有目录时显示
     **/
    private JPanel tocPanel;

    /**
     * 目录列表
     **/
    private JList<EpubUtil.TocEntry> tocList;

    /**
     * 左侧列表是否被用户收起（持久化，重启后保持）
     **/
    private boolean tocCollapsed = "1".equals(persistentState.getTocCollapsed());

    /**
     * 读取字体设置
     **/
    private String type = persistentState.getFontType();

    /**
     * 读取字号设置
     **/
    private String size = persistentState.getFontSize();

    /**
     * 读取每页行数设置
     **/
    private Integer lineCount = parseIntSafe(persistentState.getLineCount(), 1);

    /**
     * 读取行距设置
     **/
    private Integer lineSpace = parseIntSafe(persistentState.getLineSpace(), 0);

    /**
     * 阅读区字体缓存（字体枚举较慢，翻页时避免重复计算）
     **/
    private Font cachedFont;
    private String cachedFontKey;

    /**
     * 窗口根面板（热键注册宿主）
     **/
    private JPanel rootPanel;

    /**
     * 顶部伪装栏：品牌名 + 会话选择 + 页码 + 翻页/朗读图标按钮
     **/
    private JPanel headerBar;

    /**
     * 品牌名（伪装成 AI 助手的名字，可在设置文件里改，默认 CodePilot）
     **/
    private JLabel brandLabel;

    /**
     * 页码指示：伪装成"上下文/进度"计数
     **/
    private JLabel pageLabel;

    /**
     * 会话切换（= 多本书切换），仅配置了多本书时显示
     **/
    private AssistantTheme.GlyphButton bookButton;

    /**
     * 左侧"历史记录"列表的收起/展开按钮：伪装成 IDE 的侧栏开关，仅 epub 有目录时出现
     **/
    private AssistantTheme.GlyphButton tocToggleButton;

    private AssistantTheme.GlyphButton prevButton;
    private AssistantTheme.GlyphButton nextButton;
    private AssistantTheme.GlyphButton ttsButton;

    /**
     * 右下角隐形的老板键按钮（5x5，肉眼不可见）
     **/
    private JButton bossButton;

    /**
     * 对话阅读视图：正文以"助手回复 + 代码卡片"的形式渲染
     **/
    private AssistantPageView pageView;

    /**
     * 底部输入框（伪装成 AI 助手输入区，实际用于翻页/跳页）
     **/
    private ChatInputBar inputBar;

    /**
     * 老板键伪装视图：假装成 Terminal 输出
     **/
    private JTextPane bossPane;

    /**
     * 阅读区滚动面板：内容溢出时显示垂直滚动条，随窗口大小自适应
     **/
    private JScrollPane scrollPane;

    /**
     * 最近一次渲染进阅读区的原始正文（含图片占位），老板键恢复时用于还原含图片的页面
     **/
    private String lastContent;

    /**
     * 没有正文时显示的说明文案（未配置书本、读取失败等）
     **/
    private String lastNotice;

    /**
     * 正文图片占位匹配：[[IMG:文件名]]（仅用于朗读时剔除占位）
     **/
    private static final Pattern IMG_MARKER = Pattern.compile("\\[\\[IMG:([^\\]]+)]]");

    /**
     * 上一页热键（设置页可修改）
     **/
    private KeyStroke prevKeyStroke;

    /**
     * 下一页热键（设置页可修改）
     **/
    private KeyStroke nextKeyStroke;

    /**
     * 老板键（设置页可修改），全局监听。
     * 静态 AWT 监听器跨实例读取，volatile 保证设置页改键后立即对其它线程可见
     **/
    private volatile KeyStroke bossKeyStroke;

    /**
     * 朗读播放/停止热键（设置页可修改）
     **/
    private KeyStroke ttsKeyStroke;

    /**
     * 工具窗口引用，老板键隐藏时用于还原图标
     **/
    private ToolWindow toolWindow;

    /**
     * 工具窗口内容，老板键隐藏时用于修改 Tab 标题伪装
     **/
    private Content content;

    /**
     * 记录工具窗口原始图标，恢复老板键状态时还原
     **/
    private Icon originIcon;

    /**
     * 老板键伪装文案：模拟终端输出，避免被认出是小说阅读界面
     **/
    private static final String BOSS_FAKE_TEXT = "$ git status\n"
            + "On branch master\n"
            + "Your branch is up to date with 'origin/master'.\n"
            + "\n"
            + "nothing to commit, working tree clean\n"
            + "$ ";

    /**
     * 是否隐藏界面
     **/
    private boolean hide = false;

    /**
     * 标记是否有后台 IO 任务正在进行，防止翻页连点导致状态错乱
     **/
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /**
     * 刷新请求因 busy 被拦截时置位，当前 IO 任务完成后自动重试刷新，
     * 保证设置页点击 Apply 后新设置必然生效
     **/
    private final AtomicBoolean pendingRefresh = new AtomicBoolean(false);

    /**
     * 朗读服务：后台线程逐页朗读，读完自动翻下一页
     **/
    private TtsService ttsService;

    /**
     * 当前选择的朗读语音（空串表示系统默认）
     **/
    private String ttsVoice = "";

    /**
     * 当前语速倍率
     **/
    private double ttsRateValue = 1.0;

    /**
     * 系统可用语音列表（异步枚举后缓存，供朗读按钮的弹出菜单使用）
     **/
    private volatile String[] ttsVoices = new String[0];

    /**
     * 朗读启动时首屏直接朗读当前页，之后才翻页
     **/
    private volatile boolean ttsFirstPage;

    /**
     * 语音下拉中代表系统默认的项
     **/
    private static final String TTS_DEFAULT_VOICE = "System Default";

    /**
     * 可选语速倍率
     **/
    private static final String[] TTS_RATES = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};

    /**
     * 上一页动作（热键与按钮共用）
     **/
    private final Action prevAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            previousPage();
        }
    };

    /**
     * 下一页动作（热键与按钮共用）
     **/
    private final Action nextAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            nextPage();
        }
    };

    /**
     * 朗读开关动作（热键与按钮共用）
     **/
    private final Action ttsAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            toggleTts();
        }
    };

    /**
     * 全局监听（老板键 AWT 监听器 + 项目关闭清理）只注册一次的标记
     **/
    private static final AtomicBoolean globalListenersInstalled = new AtomicBoolean(false);

    /**
     * 老板键按住状态：只在"抬起 → 按下"的边沿触发，按住热键时系统的按键自动重复不会连续切换
     **/
    private static volatile boolean bossKeyDown = false;

    /**
     * 注册全局监听（只注册一次）：
     * <ul>
     *   <li>老板键 AWT 监听：任何窗口（包括设置页）获得焦点时都生效，
     *       按 {@link #instances} 里各实例当前的热键分发——旧实现每次创建工具窗口内容
     *       都 addAWTEventListener 且永不注销，多项目/重复建窗会叠加注册并泄漏；</li>
     *   <li>项目关闭清理：移除 instances 里的实例并停止朗读，避免实例随项目累积、
     *       朗读线程在窗口销毁后继续出声。</li>
     * </ul>
     **/
    private static void installGlobalListeners() {
        if (!globalListenersInstalled.compareAndSet(false, true)) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof KeyEvent) || HotkeyUtil.editingHotkey) {
                return;
            }
            KeyEvent keyEvent = (KeyEvent) event;
            boolean pressed = keyEvent.getID() == KeyEvent.KEY_PRESSED;
            if (!pressed && keyEvent.getID() != KeyEvent.KEY_RELEASED) {
                return;
            }
            int mask = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
                    | InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK;
            if (pressed) {
                if (bossKeyDown) {
                    return;
                }
                for (MainUi ui : instances.values()) {
                    if (ui.matchesBossKey(keyEvent, mask)) {
                        bossKeyDown = true;
                        ui.toggleBoss();
                    }
                }
            } else {
                // 抬起同一按键即复位（修饰键可能先松开，不能要求完全匹配），否则会漏掉下一次触发
                for (MainUi ui : instances.values()) {
                    if (ui.bossKeyCodeEquals(keyEvent)) {
                        bossKeyDown = false;
                        return;
                    }
                }
            }
        }, AWTEvent.KEY_EVENT_MASK);
        ApplicationManager.getApplication().getMessageBus().connect()
                .subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
                    @Override
                    public void projectClosed(@NotNull Project project) {
                        MainUi removed = instances.remove(project);
                        if (removed != null) {
                            removed.stopTts();
                        }
                    }
                });
    }

    private boolean matchesBossKey(KeyEvent keyEvent, int mask) {
        KeyStroke stroke = bossKeyStroke;
        return stroke != null
                && keyEvent.getKeyCode() == stroke.getKeyCode()
                && (keyEvent.getModifiersEx() & mask) == (stroke.getModifiers() & mask);
    }

    private boolean bossKeyCodeEquals(KeyEvent keyEvent) {
        KeyStroke stroke = bossKeyStroke;
        return stroke != null && keyEvent.getKeyCode() == stroke.getKeyCode();
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        try {
            instances.put(project, this);
            installGlobalListeners();
            ttsVoice = persistentState.getTtsVoice();
            ttsRateValue = parseRate(persistentState.getTtsRate());
            JPanel panel = initPanel();
            // 后台枚举系统语音，供朗读菜单使用
            populateTtsVoicesAsync();
            ContentFactory contentFactory = ContentFactory.getInstance();
            Content content = contentFactory.createContent(panel, assistantName(), false);
            // 阅读区正文是可选中文本（可聚焦组件），会排在输入框前面抢走初始焦点，
            // 用户打开面板后直接按 ↑ / ↓ 或打字就会没反应——显式声明输入框为优先聚焦组件
            content.setPreferredFocusableComponent(inputBar.inputComponent());
            toolWindow.getContentManager().addContent(content);
            this.toolWindow = toolWindow;
            this.content = content;
            // 打开工具窗口时自动加载书本并恢复上次阅读进度，无需手动点刷新
            refresh();

        } catch (Exception e) {
            LOG.error("初始化阅读面板失败", e);
        }
    }

    /**
     * 初始化整体面板：顶部伪装栏 / 中部对话区 / 左侧会话列表 / 底部输入框
     **/
    private JPanel initPanel() {
        rootPanel = new JPanel(new BorderLayout());
        pageView = new AssistantPageView(this::toast, this::loadPageIcon);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(initScrollPane(), BorderLayout.CENTER);
        center.add(initTocPanel(), BorderLayout.WEST);
        center.add(initInputBar(), BorderLayout.SOUTH);

        rootPanel.add(initHeaderBar(), BorderLayout.NORTH);
        rootPanel.add(center, BorderLayout.CENTER);
        return rootPanel;
    }

    /**
     * 阅读区滚动面板：内容溢出（窗口过小、正文超长等）时出现垂直滚动条，
     * 窗口大小变化时自动重排，无需手动设置
     **/
    private JScrollPane initScrollPane() {
        scrollPane = new JScrollPane(pageView);
        scrollPane.setBorder(JBUI.Borders.empty());
        // 与聊天区透明背景保持一致，让阅读区背景跟随工具窗口
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    /**
     * 顶部伪装栏：左边是"AI 助手"品牌名，右边是页码与翻页/朗读图标
     **/
    private JPanel initHeaderBar() {
        headerBar = new JPanel(new BorderLayout());
        headerBar.setOpaque(false);
        headerBar.setBorder(JBUI.Borders.empty(7, JBUI.scale(14), 4, JBUI.scale(10)));

        brandLabel = new JLabel(assistantName());
        brandLabel.setIcon(AssistantIcons.brand(JBUI.scale(17)));
        brandLabel.setIconTextGap(JBUI.scale(7));
        brandLabel.setFont(AssistantTheme.uiFont(12).deriveFont(Font.BOLD));
        brandLabel.setForeground(AssistantTheme.TEXT);
        brandLabel.setToolTipText("AI 编码助手");
        brandLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeRename(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeRename(e);
            }
        });

        bookButton = new AssistantTheme.GlyphButton("", AssistantTheme.uiFont(11));
        bookButton.setIcon(AssistantIcons.chevronDown(JBUI.scale(13), AssistantTheme.MUTED));
        bookButton.setHorizontalTextPosition(SwingConstants.LEFT);
        bookButton.setIconTextGap(JBUI.scale(3));
        bookButton.setBorder(JBUI.Borders.empty(2, JBUI.scale(6), 2, JBUI.scale(4)));
        bookButton.setToolTipText("切换会话");
        bookButton.addActionListener(e -> showUp(bookButton, bookMenu()));

        // 侧栏开关：图标随折叠状态在"填实/描边"之间切换，仅有目录（epub）时出现
        tocToggleButton = headerButton(AssistantIcons.sidebar(JBUI.scale(15), AssistantTheme.MUTED, !tocCollapsed),
                tocTooltip(), new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        toggleToc();
                    }
                });
        tocToggleButton.setVisible(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        left.setOpaque(false);
        left.add(brandLabel);
        left.add(tocToggleButton);
        left.add(bookButton);

        pageLabel = new JLabel();
        pageLabel.setFont(AssistantTheme.uiFont(11));
        pageLabel.setForeground(AssistantTheme.MUTED);
        pageLabel.setBorder(JBUI.Borders.empty(0, 0, 0, JBUI.scale(6)));

        prevButton = headerButton(AssistantIcons.chevronLeft(JBUI.scale(15), AssistantTheme.MUTED), "上一条回复", prevAction);
        nextButton = headerButton(AssistantIcons.chevronRight(JBUI.scale(15), AssistantTheme.MUTED), "下一条回复", nextAction);
        ttsButton = headerButton(AssistantIcons.speaker(JBUI.scale(15), AssistantTheme.MUTED, false), "语音朗读", ttsAction);
        ttsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowTtsMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowTtsMenu(e);
            }
        });

        bossButton = new JButton(" ");
        bossButton.setPreferredSize(new Dimension(5, 5));
        bossButton.setContentAreaFilled(false);
        bossButton.setBorderPainted(false);
        bossButton.setFocusable(false);
        bossButton.addActionListener(e -> toggleBoss());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0));
        right.setOpaque(false);
        right.add(pageLabel);
        right.add(prevButton);
        right.add(nextButton);
        right.add(ttsButton);
        right.add(bossButton);

        headerBar.add(left, BorderLayout.WEST);
        headerBar.add(right, BorderLayout.EAST);
        return headerBar;
    }

    private AssistantTheme.GlyphButton headerButton(Icon icon, String tooltip, Action action) {
        AssistantTheme.GlyphButton button = new AssistantTheme.GlyphButton(icon);
        button.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(22)));
        button.setToolTipText(tooltip);
        button.addActionListener(action);
        return button;
    }

    /**
     * 底部输入框：伪装成 AI 助手的输入区，实际承担翻页/跳页
     **/
    private JComponent initInputBar() {
        inputBar = new ChatInputBar(new ChatInputBar.Listener() {
            @Override
            public void onSend(String text) {
                handleInput(text);
            }

            @Override
            public void onPreviousPage() {
                previousPage();
            }

            @Override
            public void onNextPage() {
                nextPage();
            }

            @Override
            public void onModelChanged(String model) {
                persistentState.setAssistantModel(model);
            }

            @Override
            public void onRenameAssistant() {
                renameAssistant();
            }
        }, assistantModel());
        inputBar.setCustomModel(persistentState.getCustomModel());
        return inputBar;
    }

    /**
     * 左侧列表：epub 目录伪装成"历史记录"，点击可跳到对应章节
     **/
    private JPanel initTocPanel() {
        tocPanel = new JPanel(new BorderLayout());
        tocPanel.setOpaque(false);
        JLabel title = new JLabel("历史记录");
        title.setFont(AssistantTheme.uiFont(11));
        title.setForeground(AssistantTheme.MUTED);
        title.setBorder(JBUI.Borders.empty(4, JBUI.scale(12), 4, 8));
        tocList = new JList<>();
        tocList.setOpaque(false);
        tocList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tocList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EpubUtil.TocEntry) {
                    EpubUtil.TocEntry entry = (EpubUtil.TocEntry) value;
                    label.setText("  ".repeat(entry.depth) + entry.title);
                    label.setToolTipText(entry.href);
                }
                label.setFont(AssistantTheme.uiFont(11));
                label.setBorder(JBUI.Borders.empty(2, JBUI.scale(8)));
                return label;
            }
        });
        tocList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    jumpToToc(tocList.getSelectedIndex());
                }
            }
        });
        tocList.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    jumpToToc(tocList.getSelectedIndex());
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tocList);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        tocPanel.add(title, BorderLayout.NORTH);
        tocPanel.add(scroll, BorderLayout.CENTER);
        tocPanel.setPreferredSize(new Dimension(JBUI.scale(190), 0));
        tocPanel.setVisible(false);
        return tocPanel;
    }

    /**
     * 按当前 epub 目录刷新左侧列表；非 epub 或无目录时隐藏
     **/
    private void updateTocPanel() {
        if (tocPanel == null || tocList == null) {
            return;
        }
        List<EpubUtil.TocEntry> toc = currentToc();
        boolean hasToc = toc != null && !toc.isEmpty();
        if (tocToggleButton != null) {
            tocToggleButton.setVisible(hasToc && !hide);
            tocToggleButton.setIcon(AssistantIcons.sidebar(JBUI.scale(15), AssistantTheme.MUTED, !tocCollapsed));
            tocToggleButton.setToolTipText(tocTooltip());
        }
        if (!hasToc || hide || tocCollapsed) {
            tocPanel.setVisible(false);
            return;
        }
        DefaultListModel<EpubUtil.TocEntry> model = new DefaultListModel<>();
        for (EpubUtil.TocEntry entry : toc) {
            model.addElement(entry);
        }
        tocList.setModel(model);
        tocPanel.setVisible(true);
    }

    /**
     * 当前书的 epub 目录（含正文行号），非 epub / 未解包 / 尚未配置书本时为 null
     **/
    private List<EpubUtil.TocEntry> currentToc() {
        return bookSource == null ? null : bookSource.toc();
    }

    /**
     * 收起/展开左侧"历史记录"列表：折叠状态写入配置持久化，
     * 收起后正文区宽度变大，由 AssistantPageView 自己按新宽度重排换行
     **/
    private void toggleToc() {
        tocCollapsed = !tocCollapsed;
        persistentState.setTocCollapsed(tocCollapsed ? "1" : "0");
        updateTocPanel();
        if (rootPanel != null) {
            rootPanel.revalidate();
            rootPanel.repaint();
        }
    }

    /**
     * 侧栏开关按钮的提示文案，随折叠状态变化
     **/
    private String tocTooltip() {
        return tocCollapsed ? "展开历史记录" : "收起历史记录";
    }

    /**
     * 点击目录项：跳转到对应章节（该章起始行作为本页第一行）
     **/
    private void jumpToToc(int index) {
        List<EpubUtil.TocEntry> toc = currentToc();
        if (index < 0 || toc == null || index >= toc.size()) {
            return;
        }
        stopTts();
        final int line = toc.get(index).line;
        runIoAsync(() -> pager.jumpTo(line), this::applyPage);
    }

    /**
     * 翻页/跳页/重载后，按当前页所在章节同步左侧目录高亮。
     * anchor 取当前页最后一行（currentPage - 1，clamp 到 0），保证落在当前显示页内，
     * 对最后一页不足 lineCount 行的情况同样正确。
     **/
    private void syncTocSelection() {
        List<EpubUtil.TocEntry> toc = currentToc();
        if (tocList == null || toc == null || toc.isEmpty()) {
            return;
        }
        int anchor = Math.max(0, currentLine() - 1);
        int best = -1;
        int bestLine = -1;
        for (int i = 0; i < toc.size(); i++) {
            int l = toc.get(i).line;
            if (l >= 0 && l <= anchor && l >= bestLine) {
                best = i;
                bestLine = l;
            }
        }
        if (best >= 0) {
            if (tocList.getSelectedIndex() != best) {
                tocList.setSelectedIndex(best);
            }
            tocList.ensureIndexIsVisible(best);
        }
    }

    /**
     * 设置正文：把一页正文渲染成"助手回复"（开场白 + 代码卡片 + 正文 + 结尾话术）
     **/
    private void setPageText(String content) {
        lastContent = content;
        lastNotice = null;
        if (pageView == null) {
            return;
        }
        pageView.render(content, pageSeed(), resolveFont(), paragraphGap(),
                persistentState.getCodeLanguage(), persistentState.isShellBlockEnabled(),
                !persistentState.isHideImages());
        scrollToTop();
    }

    /**
     * 显示一条说明文案（未配置书本、读取失败等），不插入代码卡片
     **/
    private void setNoticeText(String message) {
        lastContent = null;
        lastNotice = message;
        if (pageView == null) {
            return;
        }
        pageView.renderNotice(message, resolveFont());
        scrollToTop();
    }

    /**
     * 重新渲染当前内容（老板键恢复、切书后刷新）
     **/
    private void renderCurrent() {
        if (lastContent != null) {
            setPageText(lastContent);
        } else if (lastNotice != null) {
            setNoticeText(lastNotice);
        } else {
            setNoticeText(DisguiseContent.WELCOME);
        }
    }

    /**
     * 翻页/跳页/朗读推进后的统一落地：渲染正文、保存进度、刷新页码与目录高亮。
     * 原先这段回调在五处逐字重复，改这里即可全部生效
     **/
    private void applyPage(String content) {
        setPageText(content);
        saveProgress();
        updatePageInfo();
        syncTocSelection();
    }

    private void scrollToTop() {
        if (scrollPane != null) {
            SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
        }
    }

    /**
     * 伪装代码卡片的选取种子：同一本书同一页始终一致，翻页后换一批
     **/
    private int pageSeed() {
        return Math.floorMod(Objects.hash(bookFile, currentLine()), 100000);
    }

    /**
     * 已配置书本
     **/
    private boolean hasBook() {
        return bookFile != null && !bookFile.isEmpty();
    }

    /**
     * 当前页码（= 已读过的行数，见 BookPager 的页码约定）
     **/
    private int currentLine() {
        BookPager p = pager;
        return p == null ? 0 : p.currentPage();
    }

    /**
     * 当前文件总行数（尚未统计时为 0）
     **/
    private int totalLineCount() {
        BookPager p = pager;
        return p == null ? 0 : p.totalLines();
    }

    /**
     * 行距设置换算成段落间距
     **/
    private int paragraphGap() {
        int space = lineSpace == null ? 0 : Math.max(0, Math.min(lineSpace, 10));
        return JBUI.scale(3 + space * 4);
    }

    /**
     * 解析阅读区字体：未配置/选择"系统默认"/系统不存在的字体时，跟随 IDE 默认字体。
     * 枚举系统字体较慢，按"字体名 + 字号"缓存，翻页时不再重复枚举。
     **/
    private Font resolveFont() {
        String cacheKey = type + "|" + size;
        if (cachedFont != null && cacheKey.equals(cachedFontKey)) {
            return cachedFont;
        }
        Font resolved;
        int s = parseIntSafe(size, 14);
        if (type != null && !type.isEmpty() && !PersistentState.DEFAULT_FONT.equals(type)) {
            resolved = null;
            for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (type.equals(family)) {
                    resolved = UIUtil.getFontWithFallback(new Font(type, Font.PLAIN, s));
                    break;
                }
            }
            if (resolved == null) {
                resolved = UIUtil.getFontWithFallback(UIUtil.getLabelFont()).deriveFont(Font.PLAIN, s);
            }
        } else {
            resolved = UIUtil.getFontWithFallback(UIUtil.getLabelFont()).deriveFont(Font.PLAIN, s);
        }
        cachedFont = resolved;
        cachedFontKey = cacheKey;
        return resolved;
    }

    /**
     * 按图片临时目录里的文件名加载图片并等比缩放到阅读区宽度内，加载失败返回 null
     **/
    private Icon loadPageIcon(String fileName) {
        File imageDir = bookSource == null ? null : bookSource.imageDir();
        if (imageDir == null) {
            return null;
        }
        File file = new File(imageDir, fileName);
        if (!file.isFile()) {
            return null;
        }
        ImageIcon icon = new ImageIcon(file.getAbsolutePath());
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int maxWidth = Math.max(200, (scrollPane != null ? scrollPane.getViewport().getWidth() : 600) - 60);
        int maxHeight = 480;
        double scale = Math.min(1.0, Math.min((double) maxWidth / w, (double) maxHeight / h));
        if (scale < 1.0) {
            icon.setImage(icon.getImage().getScaledInstance(
                    Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)), Image.SCALE_SMOOTH));
        }
        return icon;
    }

    /**
     * 底部输入框被"发送"后的处理：翻页指令、跳页、或伪装成助手在思考
     **/
    private void handleInput(String text) {
        if (text == null || text.trim().isEmpty()) {
            // 空回车 = 继续
            nextPage();
            return;
        }
        String input = text.trim();
        String lower = input.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "/next":
                nextPage();
                return;
            case "/prev":
                previousPage();
                return;
            case "/play":
                startTts();
                return;
            case "/stop":
                stopTts();
                toast("已停止朗读");
                return;
            case "/boss":
                toggleBoss();
                return;
            case "/help":
                toast("可用指令：/next /prev /play /stop /boss 或直接输入页码");
                return;
            default:
                break;
        }
        String pageArg = lower.startsWith("/page") ? input.substring("/page".length()).trim() : input;
        if (pageArg.matches("\\d+")) {
            jumpToPage(Integer.parseInt(pageArg));
            return;
        }
        // 其它输入：先显示"正在分析"，再给一句像样的助手回复，纯伪装
        toast(DisguiseContent.TOAST_THINKING);
        final int seed = Math.floorMod(input.hashCode(), 100000);
        Timer timer = new Timer(680, e -> toast(DisguiseContent.reply(seed)));
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * 跳到第 N 页（页码从 1 开始）
     **/
    private void jumpToPage(int page) {
        if (totalLineCount() <= 0) {
            toast(DisguiseContent.TOAST_BOOK);
            return;
        }
        stopTts();
        int startLine;
        if (page <= 1) {
            startLine = 0;
        } else {
            startLine = (page - 1) * lineCount;
            if (startLine > totalLineCount()) {
                startLine = Math.max(0, totalLineCount() - 1);
                toast(DisguiseContent.TOAST_PAGE_OUT + totalPages() + DisguiseContent.TOAST_PAGES);
            } else {
                toast(DisguiseContent.TOAST_PAGE_JUMP + page + DisguiseContent.TOAST_PAGES);
            }
        }
        final int target = startLine;
        runIoAsync(() -> pager.jumpTo(target), this::applyPage);
    }

    /**
     * 下一页（发送按钮 / 下一条回复按钮 / Ctrl+2）
     **/
    private void nextPage() {
        stopTts();
        if (!hasBook()) {
            toast(DisguiseContent.TOAST_BOOK);
            return;
        }
        if (currentLine() >= totalLineCount()) {
            toast("已到工作区末尾");
            return;
        }
        runIoAsync(() -> pager.turnNext(), this::applyPage);
    }

    /**
     * 上一页（上一条回复按钮 / Ctrl+1）
     **/
    private void previousPage() {
        stopTts();
        if (!hasBook()) {
            toast(DisguiseContent.TOAST_BOOK);
            return;
        }
        if (currentLine() > totalLineCount()) {
            return;
        }
        if (currentLine() / lineCount <= 1) {
            toast("已经是第一条");
            return;
        }
        runIoAsync(() -> pager.turnBack(), this::applyPage);
    }

    /**
     * 从设置读取热键并绑定：上一页/下一页/朗读仅在工具窗口内生效，老板键全局生效。
     * refresh() 会重新调用本方法，使设置页修改的热键即时生效。
     **/
    private void updateHotkeys() {
        KeyStroke oldPrev = prevKeyStroke;
        KeyStroke oldNext = nextKeyStroke;
        KeyStroke oldTts = ttsKeyStroke;
        prevKeyStroke = HotkeyUtil.parse(persistentState.getBefore());
        nextKeyStroke = HotkeyUtil.parse(persistentState.getNext());
        bossKeyStroke = HotkeyUtil.parse(persistentState.getBossKey());
        ttsKeyStroke = HotkeyUtil.parse(persistentState.getTtsKey());
        if (rootPanel == null) {
            return;
        }
        if (oldPrev != null) {
            rootPanel.unregisterKeyboardAction(oldPrev);
        }
        if (oldNext != null) {
            rootPanel.unregisterKeyboardAction(oldNext);
        }
        if (oldTts != null) {
            rootPanel.unregisterKeyboardAction(oldTts);
        }
        if (prevKeyStroke != null) {
            rootPanel.registerKeyboardAction(prevAction, prevKeyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        if (nextKeyStroke != null) {
            rootPanel.registerKeyboardAction(nextAction, nextKeyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        if (ttsKeyStroke != null) {
            rootPanel.registerKeyboardAction(ttsAction, ttsKeyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
    }

    /**
     * 重新读取设置并应用到阅读界面。
     * 打开窗口、切书与设置页 apply() 均调用本方法，设置修改后无需手动刷新即可生效。
     **/
    public void refresh() {
        if (pageView == null) {
            return;
        }
        stopTts();
        try {
            persistentState = PersistentState.getInstance();
            String bookPath = persistentState.getBookPathText();
            // 切书（含清空书本、首次加载 pager 尚未创建）时重建读取引擎：
            // 新 BookSource 会重置编码缓存 / epub 解包状态，新 BookPager 会重置页码与指针缓存
            boolean bookChanged = pager == null
                    || (bookPath == null || bookPath.isEmpty())
                    || !Objects.equals(bookFile, bookPath);
            if (bookChanged) {
                bookFile = bookPath;
                bookSource = new BookSource(bookFile);
                pager = new BookPager(bookSource);
                // 切书后从该书独立保存的进度恢复
                pager.setCurrentPage(parseIntSafe(persistentState.getCurrentLineFor(bookFile), 0));
            } else {
                pager.setCurrentPage(parseIntSafe(persistentState.getCurrentLineFor(bookFile), pager.currentPage()));
            }
            type = persistentState.getFontType();
            size = persistentState.getFontSize();
            // 行数至少为 1：手改配置文件写成 0 会让页码换算除零
            lineCount = Math.max(1, parseIntSafe(persistentState.getLineCount(), lineCount));
            lineSpace = Math.max(0, parseIntSafe(persistentState.getLineSpace(), lineSpace));
            pager.setLinesPerPage(lineCount);
            pager.setLineSpacing(lineSpace);
            if (brandLabel != null) {
                brandLabel.setText(assistantName());
            }
            if (content != null && !hide) {
                content.setDisplayName(assistantName());
            }
            if (inputBar != null) {
                inputBar.setModel(assistantModel());
                inputBar.setCustomModel(persistentState.getCustomModel());
            }
            updateHotkeys();
            updateBookButton();
            updateTtsButton();

            if (!hasBook()) {
                setNoticeText(DisguiseContent.WELCOME);
                updatePageInfo();
                updateTocPanel();
                return;
            }
            // 仅在切书或尚未统计过时才全量扫描行数，避免每次刷新都重扫大文件
            final boolean needCount = bookChanged || pager.totalLines() == 0;
            final BookPager p = pager;
            if (!runIoAsync(() -> {
                if (needCount) {
                    p.countLines();
                }
                // 重新定位到当前页起点并重读该页正文，保持阅读进度不变
                return p.reloadCurrent();
            }, content -> {
                setPageText(content);
                updatePageInfo();
                updateTocPanel();
                syncTocSelection();
            })) {
                // 正在翻页/读取中：标记稍后自动重试，确保设置修改必然生效
                pendingRefresh.set(true);
            }
        } catch (Exception newE) {
            LOG.error("应用设置失败", newE);
        }
    }

    /**
     * 老板键：隐藏/恢复阅读界面（伪装成 Terminal）。
     * 隐藏时整个工具窗口伪装成终端（修改 Tab 标题、图标与内容，并收起对话区与输入框），
     * 由老板键按钮与全局快捷键（设置页可配置）共同触发，
     * 任何窗口（包括设置页）获得焦点时都生效。
     **/
    public void toggleBoss() {
        if (!hide) {
            stopTts();
            if (headerBar != null) {
                headerBar.setVisible(false);
            }
            if (inputBar != null) {
                inputBar.setVisible(false);
            }
            if (tocPanel != null) {
                tocPanel.setVisible(false);
            }
            if (scrollPane != null) {
                scrollPane.setViewportView(bossView());
            }
            if (content != null) {
                content.setDisplayName("Terminal");
            }
            if (toolWindow != null) {
                if (originIcon == null) {
                    originIcon = toolWindow.getIcon();
                }
                toolWindow.setIcon(AllIcons.Debugger.Console);
            }
            hide = true;
        } else {
            if (headerBar != null) {
                headerBar.setVisible(true);
            }
            if (inputBar != null) {
                inputBar.setVisible(true);
            }
            if (scrollPane != null) {
                scrollPane.setViewportView(pageView);
            }
            renderCurrent();
            updateTocPanel();
            updateBookButton();
            if (content != null) {
                content.setDisplayName(assistantName());
            }
            if (toolWindow != null) {
                toolWindow.setIcon(originIcon);
            }
            hide = false;
        }
    }

    /**
     * 老板键伪装内容：一段终端输出（懒创建）
     **/
    private JTextPane bossView() {
        if (bossPane == null) {
            bossPane = new JTextPane();
            bossPane.setEditable(false);
            bossPane.setBorder(JBUI.Borders.empty(8, JBUI.scale(12)));
            Font font = UIUtil.getFontWithFallback(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            bossPane.setFont(font);
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontFamily(attrs, font.getFamily());
            StyleConstants.setFontSize(attrs, font.getSize());
            StyleConstants.setForeground(attrs, AssistantTheme.TEXT);
            StyledDocument doc = bossPane.getStyledDocument();
            try {
                doc.insertString(0, BOSS_FAKE_TEXT, attrs);
            } catch (BadLocationException ignored) {
                bossPane.setText(BOSS_FAKE_TEXT);
            }
        }
        return bossPane;
    }

    /**
     * 右侧/底部共用的提示条：显示在输入框上方
     **/
    private void toast(String message) {
        if (inputBar != null && !hide) {
            inputBar.showToast(message);
        }
    }

    /**
     * 品牌名（伪装用）
     **/
    private String assistantName() {
        return persistentState.getAssistantName();
    }

    /**
     * 模型名（伪装用，显示在输入框右下角）
     **/
    private String assistantModel() {
        return persistentState.getAssistantModel();
    }

    /**
     * 品牌名右键：重命名助手 / 切换模型（伪装设置入口，避免动设置页布局）
     **/
    private void maybeRename(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("重命名助手…");
        rename.addActionListener(a -> renameAssistant());
        menu.add(rename);
        JMenuItem model = new JMenuItem("切换模型…");
        model.addActionListener(a -> inputBar.setModel(persistentState.getAssistantModel()));
        menu.add(model);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * 重命名"助手"：只影响界面上的伪装文案，不改变插件功能
     **/
    private void renameAssistant() {
        String input = JOptionPane.showInputDialog(rootPanel, "助手名称", assistantName());
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        persistentState.setAssistantName(input.trim());
        if (brandLabel != null) {
            brandLabel.setText(input.trim());
        }
        if (content != null && !hide) {
            content.setDisplayName(input.trim());
        }
    }

    /**
     * 切换"会话"（= 切换书本），进度按书独立保存
     **/
    private JPopupMenu bookMenu() {
        JPopupMenu menu = new JPopupMenu();
        List<String> books = persistentState.getBookPathList();
        for (int i = 0; i < books.size(); i++) {
            String path = books.get(i);
            JRadioButtonMenuItem item = new JRadioButtonMenuItem("会话 " + (i + 1), Objects.equals(path, bookFile));
            item.setToolTipText(path);
            item.addActionListener(e -> {
                stopTts();
                persistentState.setBookPathText(path);
                refresh();
            });
            menu.add(item);
        }
        return menu;
    }

    /**
     * 同步"会话"按钮文案与可见性（仅多本书时显示）
     **/
    private void updateBookButton() {
        if (bookButton == null) {
            return;
        }
        List<String> books = persistentState.getBookPathList();
        int index = books.indexOf(bookFile);
        bookButton.setText("会话 " + (index >= 0 ? index + 1 : 1));
        bookButton.setVisible(books.size() > 1 && !hide);
    }

    /**
     * 菜单显示在按钮上方（底部输入区更自然）
     **/
    private void showUp(JComponent invoker, JPopupMenu menu) {
        menu.show(invoker, 0, -menu.getPreferredSize().height - JBUI.scale(2));
    }

    /**
     * 开始朗读：从当前页起，读完自动翻下一页
     **/
    private void startTts() {
        TtsService service = ttsService;
        // 只拦"真正在播"的服务：停止中（已请求停止但线程尚未退出）的实例直接替换掉，
        // 否则停止后立刻再点一次会被 isRunning 挡住、没有反应
        if (service != null && service.isActive()) {
            return;
        }
        if (!hasBook()) {
            toast(DisguiseContent.TOAST_BOOK);
            return;
        }
        if (!TtsEngines.isSupported()) {
            toast("当前环境不支持语音播放");
            return;
        }
        ttsFirstPage = true;
        ttsService = new TtsService(this::ttsNextPage, this::onTtsStateChanged);
        ttsService.setVoice(ttsVoice);
        ttsService.setRate(ttsRateValue);
        ttsService.start();
        updateTtsButton();
    }

    /**
     * 停止朗读
     **/
    private void stopTts() {
        TtsService service = ttsService;
        if (service != null) {
            service.stop();
        }
    }

    /**
     * 播放 / 停止切换
     **/
    private void toggleTts() {
        TtsService service = ttsService;
        if (service != null && service.isActive()) {
            service.stop();
            toast("已停止朗读");
        } else {
            startTts();
            toast("开始朗读当前回复");
        }
    }

    /**
     * 朗读线程取下一页文本：首次返回当前显示页，之后翻页读取。
     * 取页走 BookPager（内部按复合操作串行），与手动翻页不会交错推进页码；
     * 翻页后通过 invokeLater 回到 EDT 更新界面与进度。
     **/
    private String ttsNextPage() {
        BookPager p = pager;
        if (!hasBook() || p == null) {
            return null;
        }
        try {
            if (ttsFirstPage) {
                ttsFirstPage = false;
                String shown = lastContent != null ? lastContent : "";
                return cleanForTts(shown);
            }
            if (p.totalLines() > 0 && p.currentPage() >= p.totalLines()) {
                return null;
            }
            int before = p.currentPage();
            final String content = p.turnNext();
            if (p.currentPage() == before && cleanForTts(content).isEmpty()) {
                return null;
            }
            ApplicationManager.getApplication().invokeLater(() -> applyPage(content));
            return cleanForTts(content);
        } catch (Exception e) {
            LOG.warn("朗读取页失败", e);
            return null;
        }
    }

    /**
     * 去掉 epub 图片占位等非朗读内容
     **/
    private String cleanForTts(String text) {
        if (text == null) {
            return "";
        }
        return IMG_MARKER.matcher(text).replaceAll(" ").trim();
    }

    /**
     * 朗读状态变化（后台线程回调）：切回 EDT 刷新按钮
     **/
    private void onTtsStateChanged() {
        try {
            ApplicationManager.getApplication().invokeLater(this::updateTtsButton);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 按朗读状态刷新喇叭按钮图标与提示
     **/
    private void updateTtsButton() {
        if (ttsButton == null) {
            return;
        }
        boolean running = ttsService != null && ttsService.isRunning();
        ttsButton.setIcon(AssistantIcons.speaker(JBUI.scale(15),
                running ? AssistantTheme.ACCENT : AssistantTheme.MUTED, running));
        ttsButton.setToolTipText((running ? "停止朗读" : "语音朗读")
                + " · " + (ttsVoice.isEmpty() ? TTS_DEFAULT_VOICE : ttsVoice)
                + " · " + rateLabel(ttsRateValue));
    }

    /**
     * 朗读按钮右键：伪装成"语音与语速"设置
     **/
    private void maybeShowTtsMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenu voice = new JMenu("Voice");
        JRadioButtonMenuItem defaultVoice = new JRadioButtonMenuItem(TTS_DEFAULT_VOICE, ttsVoice.isEmpty());
        defaultVoice.addActionListener(a -> selectTtsVoice(""));
        voice.add(defaultVoice);
        String[] voices = ttsVoices;
        if (voices.length == 0) {
            populateTtsVoicesAsync();
        }
        for (String name : voices) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(name, name.equals(ttsVoice));
            item.addActionListener(a -> selectTtsVoice(name));
            voice.add(item);
        }
        menu.add(voice);

        JMenu rate = new JMenu("Rate");
        for (String label : TTS_RATES) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label,
                    Math.abs(parseRate(label) - ttsRateValue) < 0.001);
            item.addActionListener(a -> {
                ttsRateValue = parseRate(label);
                persistentState.setTtsRate(String.valueOf(ttsRateValue));
                applyTtsConfigToService();
                updateTtsButton();
            });
            rate.add(item);
        }
        menu.add(rate);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * 选择语音：写入配置，正在朗读时即时生效
     **/
    private void selectTtsVoice(String voice) {
        ttsVoice = voice == null ? "" : voice;
        persistentState.setTtsVoice(ttsVoice);
        applyTtsConfigToService();
        updateTtsButton();
    }

    /**
     * 异步枚举系统语音，供朗读菜单使用（Windows 走 SAPI，可能耗时几百毫秒）
     **/
    private void populateTtsVoicesAsync() {
        if (!TtsEngines.isSupported()) {
            return;
        }
        Thread thread = new Thread(() -> ttsVoices = TtsEngines.listVoices(), "thief-book-tts-voices");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 语音/语速变化时：正在朗读则即时生效
     **/
    private void applyTtsConfigToService() {
        if (ttsService != null && ttsService.isRunning()) {
            ttsService.setVoice(ttsVoice);
            ttsService.setRate(ttsRateValue);
        }
    }

    private static double parseRate(String rate) {
        try {
            return Double.parseDouble(rate == null ? null : rate.trim());
        } catch (Exception e) {
            return 1.0;
        }
    }

    /**
     * 把倍率数值映射为下拉标签，找不到时回退 1.0x
     **/
    private static String rateLabel(double rate) {
        for (String label : TTS_RATES) {
            if (Math.abs(parseRate(label.replace("x", "").trim()) - rate) < 0.001) {
                return label;
            }
        }
        return "1.0x";
    }

    /**
     * 把 IO 任务放到后台线程池执行，完成后回到 EDT 更新界面。
     * busy 标志保证同一时刻只有一个翻页/读取任务在跑，避免连点导致的状态错乱。
     * 返回是否成功启动；busy 中调用会返回 false，由调用方决定是否稍后重试。
     * <p>
     * 文件读取逻辑已搬到 {@link BookPager} / {@link BookSource}；异常在这里统一转成
     * "无法读取当前工作区上下文"的说明文案（不再把异常消息混进正文渲染）。
     **/
    private boolean runIoAsync(IoSupplier ioSupplier, Consumer<String> onEdt) {

        if (!busy.compareAndSet(false, true)) {
            return false;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String content;
            try {
                content = ioSupplier.get();
            } catch (Exception e) {
                LOG.warn("读取工作区上下文失败", e);
                final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                ApplicationManager.getApplication().invokeLater(() -> {
                    setNoticeText("无法读取当前工作区上下文：" + msg);
                    busy.set(false);
                    retryPendingRefresh();
                });
                return;
            }
            final String result = content;
            ApplicationManager.getApplication().invokeLater(() -> {
                onEdt.accept(result);
                busy.set(false);
                retryPendingRefresh();
            });
        });
        return true;
    }

    /**
     * 若存在被 busy 拦截的刷新请求，在 EDT 上重新执行一次 refresh()
     **/
    private void retryPendingRefresh() {
        if (pendingRefresh.compareAndSet(true, false)) {
            refresh();
        }
    }

    @FunctionalInterface
    private interface IoSupplier {
        String get() throws IOException;
    }

    /**
     * 在 EDT 上保存当前阅读进度（按书独立保存）
     **/
    private void saveProgress() {
        persistentState.setCurrentLineFor(bookFile, String.valueOf(currentLine()));
    }

    /**
     * 刷新页码显示（伪装成上下文进度计数）
     **/
    private void updatePageInfo() {
        if (pageLabel == null) {
            return;
        }
        String text = displayPage() + " / " + totalPages();
        pageLabel.setText(text);
        pageLabel.setToolTipText("上下文 " + text);
    }

    /**
     * 当前页号（从 1 开始），最后不足一页时按已读满的页数 + 1 显示
     **/
    private int displayPage() {
        if (lineCount <= 0) {
            return 0;
        }
        int cur = currentLine();
        return cur % lineCount == 0 ? cur / lineCount : cur / lineCount + 1;
    }

    private int totalPages() {
        if (lineCount <= 0) {
            return 0;
        }
        int total = totalLineCount();
        return total % lineCount == 0 ? total / lineCount : total / lineCount + 1;
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

}
