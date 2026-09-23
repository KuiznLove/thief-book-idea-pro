package com.thief.idea.ui;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.ui.JBUI;
import com.thief.idea.disguise.DisguiseContent;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阅读页的伪装视图：把一页小说正文渲染成"AI 编码助手的回复"。
 * <p>
 * 结构（自上而下）：
 * <ol>
 *   <li>助手开场白（伪装话术）</li>
 *   <li>伪造的代码 diff 卡片（带文件名、复制/导出/应用按钮）</li>
 *   <li>小说正文段落（每行一段）</li>
 *   <li>正文中按比例插入的小标题 + 第二个代码卡片</li>
 *   <li>助手结尾话术</li>
 * </ol>
 * 除了段落之间的 diff 卡片，还会（开关打开时）在某些自然段<strong>内部</strong>插入一条单行
 * shell 命令，把段落切成两半；两类代码块彼此独立，见 {@link #shellBlock(DisguiseContent.ShellScript, int)}。
 * 长段落从内部跨越字数阈值时，diff 卡片也会从句末标点处切开段落插进去（见
 * {@link #cardSplitPoint}），保证卡片间隔不被整段长度撑大——否则会出现满屏正文、
 * 一张代码卡片都看不到的页面。
 * <p>
 * 正文里的英文/数字会被渲染成行内代码块（灰底等宽），与真实助手回复的排版一致。
 * <p>
 * 本面板直接作为滚动面板的视图使用，实现 {@link Scrollable} 让其宽度跟随视口，
 * 这样段落的自动换行高度才能算准（否则 BoxLayout 下文本高度会错乱）。
 **/
public final class AssistantPageView extends JPanel implements Scrollable {

    /**
     * epub 正文中的图片占位：[[IMG:文件名]]
     **/
    private static final Pattern IMG_MARKER = Pattern.compile("\\[\\[IMG:([^\\]]+)]]");

    /**
     * 可伪装成行内代码的文本片段：英文标识符或数字
     **/
    private static final Pattern INLINE_CODE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{2,24}|\\d+(?:\\.\\d+)?");

    /**
     * 每段最多渲染几个行内代码块，避免满屏灰底
     **/
    private static final int MAX_INLINE_CODE = 3;

    /**
     * 一页里最多插入几张伪装代码卡片（不含顶部那张）。
     * `DisguiseContent` 的片段池有 8 个，按种子取模后互不重复（顶部那张占 0 号），
     * 所以这里最多只能排到 7，再多就会出现同一页两张一样的卡片
     **/
    private static final int MAX_EXTRA_CARDS = 7;

    /**
     * 每隔大约这么多字符安排一张伪装代码卡片。
     * <p>
     * 两个取值依据：
     * <ol>
     *   <li>卡片按"累计字数"分布，而不是按"第几段"分布——段落长短差异很大（长段能占满一屏、
     *   短段只有一行），按段落序号均分会出现连续好几屏全是正文。</li>
     *   <li>250 字约等于 13 行正文（一屏 400~500px 能显示的行数），
     *   保证无论滚到哪一屏都至少能看见一张卡片；一页只有几百字时靠顶部那张兜底。</li>
     * </ol>
     * 跨越阈值的<b>长段落</b>会在段落内部的句末标点处切开插卡（见 {@link #cardSplitPoint}），
     * 不再等整段结束——否则间隔会被整段长度撑大，出现满屏正文没有代码块的情况
     **/
    private static final int CHARS_PER_CARD = 250;

    /**
     * 段内插卡只考虑长于这个字数的段落：短段落按"段后插卡"的误差本来就小，
     * 切开反而显得碎
     **/
    private static final int MIN_CARD_SPLIT_PARAGRAPH = 100;

    /**
     * 段内插卡的切点搜索窗口：在目标位置（字数阈值落在段内的位置）前后各这么多字里
     * 找最近的句末标点
     **/
    private static final int CARD_SPLIT_BAND = 75;

    /**
     * 段内插卡的切点距段首/段尾的最小字数：保证切出来的两半都还是"成段的正文"，
     * 不会出现卡片后面只挂着半句话的尾巴
     **/
    private static final int CARD_SPLIT_EDGE = 24;

    /**
     * 一页里最多插入几条单行 shell 命令。
     * <p>
     * 它比 diff 卡片的"侵入性"更强——会把一个完整的自然段切成两半——所以宁少勿多：
     * 一条已经足够让人扫一眼就觉得"这助手刚跑过命令"，两条是上限，再多就影响正常阅读了
     **/
    private static final int MAX_SHELL_BLOCKS = 2;

    /**
     * 只有长于这个字数、且中间能找到句末标点的段落才会被 shell 命令切开。
     * <p>
     * 取值是权衡出来的：60 字（约两行）切出来最自然，但那样大部分页面根本轮不到
     * （实测一段 25 段的正文里只有个位数段落达标），这个功能也就白加了；
     * 40 字（一行多一点）是"还看得出是两截、又足够常见"的下限
     **/
    private static final int MIN_SHELL_PARAGRAPH = 40;

    private final Consumer<String> onToast;

    /**
     * epub 图片加载器：传入图片文件名返回可插入文档的图标，null 表示没有图片
     **/
    private final Function<String, Icon> imageLoader;

    private Font bodyFont = AssistantTheme.uiFont(14);
    private int paragraphGap = JBUI.scale(7);
    private int laidOutWidth = -1;

    public AssistantPageView(Consumer<String> onToast, Function<String, Icon> imageLoader) {
        super();
        this.onToast = onToast;
        this.imageLoader = imageLoader;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(JBUI.Borders.empty(10, 18, 16, 18));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyContentWidth();
            }
        });
    }

    /**
     * 渲染一页小说正文
     *
     * @param novelText 一页正文（可能含 epub 图片占位）
     * @param seed      由书路径 + 页码派生的种子，保证同一页每次渲染结果一致
     * @param font      正文字体（来自设置页）
     * @param gapPx     行间距设置换算出的段间距
     * @param language  伪装代码卡片使用的语言（见 {@link DisguiseContent#LANGUAGES}），null / 未知值回退到默认语言
     * @param shellEnabled 是否在自然段中间插单行 shell 命令（设置页开关，见 {@link DisguiseContent#SHELL_SCRIPTS}）
     * @param imageEnabled 是否渲染 epub 插图（设置页 "Hide book images" 的反值）；false 时图片占位会被剔除
     **/
    public void render(String novelText, int seed, Font font, int gapPx, String language,
                       boolean shellEnabled, boolean imageEnabled) {
        if (font != null) {
            bodyFont = font;
        }
        paragraphGap = Math.max(JBUI.scale(4), gapPx);
        String lang = language == null ? DisguiseContent.DEFAULT_LANGUAGE : language;
        removeAll();
        List<String> paragraphs = splitParagraphs(novelText);
        if (!imageEnabled) {
            paragraphs = withoutImages(paragraphs);
        }

        addBlock(statusLine(DisguiseContent.intro(seed)));
        addBlock(codeCard(DisguiseContent.snippet(lang, seed, 0), 0));
        addBlock(gap(paragraphGap + JBUI.scale(4)));

        // 单行 shell 块：先把"能被切开"的候选段落挑出来，再均匀取最多 MAX_SHELL_BLOCKS 个。
        // 与 diff 卡片的两套逻辑互不干扰——卡片落在段落之间，shell 块落在段落内部
        List<Integer> shellSlots = shellEnabled ? shellSlots(paragraphs, seed) : new ArrayList<>();

        // 卡片密度由整页字数决定：每 CHARS_PER_CARD 个字安排一张（上限 MAX_EXTRA_CARDS），
        // 再把等分出来的"字数阈值"落到具体段落之后，保证页内分布均匀
        int totalChars = 0;
        for (String paragraph : paragraphs) {
            totalChars += paragraph.length();
        }
        int extraCards = Math.min(MAX_EXTRA_CARDS, totalChars / CHARS_PER_CARD);
        int placed = 0;
        int consumed = 0;
        int shellPlaced = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i);
            // 命中候选段落时，从中间那个句末标点处把段落切成两半，命令插在两半之间。
            // 但本段若跨越了字数阈值，卡片优先——shell 只是装饰，长段落被 shell 独占
            // 会退化成"一条命令 + 满屏正文没有卡片"
            int cut = shellSlots.contains(i) ? splitPoint(text) : -1;
            if (cut > 0 && cardSplitCut(text, 0, consumed, placed, extraCards, totalChars) > 0) {
                cut = -1;
            }
            if (cut > 0) {
                addBlock(paragraph(text.substring(0, cut)));
                addBlock(gap(JBUI.scale(5)));
                addBlock(shellBlock(DisguiseContent.shellScript(seed, shellPlaced), shellPlaced));
                addBlock(gap(JBUI.scale(5)));
                addBlock(paragraph(text.substring(cut)));
                shellPlaced++;
            } else {
                // 段内插卡（可能连续多刀）：长段落会接连跨越多个字数阈值，若仍等"段后"落卡，
                // 卡片间隔会被整段长度撑大（超长段一张卡都拦不住，整屏都是正文）。
                // 每跨过一个阈值就从句末标点处切开插一张卡，直到剩余部分不再跨阈值，
                // 间隔超冲被限制在一句话以内
                int pos = 0;
                while (placed < extraCards) {
                    int cardCut = cardSplitCut(text, pos, consumed, placed, extraCards, totalChars);
                    if (cardCut <= pos) {
                        break;
                    }
                    addBlock(paragraph(text.substring(pos, cardCut)));
                    addBlock(gap(JBUI.scale(5)));
                    addBlock(heading(DisguiseContent.sectionTitle(seed, placed)));
                    addBlock(gap(JBUI.scale(3)));
                    addBlock(codeCard(DisguiseContent.snippet(lang, seed, placed + 1), placed + 1));
                    addBlock(gap(JBUI.scale(5)));
                    placed++;
                    pos = cardCut;
                }
                if (pos > 0 && pos < text.length()) {
                    addBlock(paragraph(text.substring(pos)));
                } else if (pos == 0) {
                    addBlock(paragraph(text));
                }
            }
            addBlock(gap(paragraphGap));
            consumed += text.length();
            boolean lastParagraph = i == paragraphs.size() - 1;
            if (lastParagraph || placed >= extraCards) {
                continue;
            }
            double threshold = totalChars * (placed + 1) / (double) (extraCards + 1);
            if (consumed >= threshold) {
                addBlock(heading(DisguiseContent.sectionTitle(seed, placed)));
                addBlock(gap(JBUI.scale(3)));
                addBlock(codeCard(DisguiseContent.snippet(lang, seed, placed + 1), placed + 1));
                addBlock(gap(paragraphGap + JBUI.scale(4)));
                placed++;
            }
        }
        // 兜底：整页只有一两个超长段落时段落之间没有落脚点，至少在结尾话术前补一张，
        // 避免"从头翻到尾都看不到代码块"
        if (placed == 0 && extraCards > 0) {
            addBlock(heading(DisguiseContent.sectionTitle(seed, 0)));
            addBlock(gap(JBUI.scale(3)));
            addBlock(codeCard(DisguiseContent.snippet(lang, seed, 1), 1));
            addBlock(gap(paragraphGap + JBUI.scale(4)));
        }
        addBlock(statusLine(DisguiseContent.closing(seed)));

        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::applyContentWidth);
    }

    /**
     * 渲染一条"助手说明"（未配置书本、读取失败等场景），不插入代码卡片
     **/
    public void renderNotice(String message, Font font) {
        if (font != null) {
            bodyFont = font;
        }
        removeAll();
        List<String> paragraphs = splitParagraphs(message);
        if (paragraphs.isEmpty()) {
            paragraphs.add("");
        }
        for (String text : paragraphs) {
            addBlock(statusLine(text));
            addBlock(gap(paragraphGap));
        }
        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::applyContentWidth);
    }

    /**
     * 文本宽度跟随视口宽度变化时分发给所有段落，保证换行高度正确
     **/
    private void applyContentWidth() {
        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        if (width <= 0 || width == laidOutWidth) {
            return;
        }
        laidOutWidth = width;
        for (Component component : getComponents()) {
            if (component instanceof ParagraphPane) {
                ((ParagraphPane) component).setWrapWidth(width);
            }
        }
        revalidate();
    }

    /**
     * 拆分正文段落：去掉空行与行尾 \r
     **/
    private static List<String> splitParagraphs(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (!trimmed.trim().isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 无图模式的段落预处理：删掉图片占位，并丢弃"整段只有插图"的段落。
     * <p>
     * epub 解包时插图是**单独一行**的 `[[IMG:文件名]]`（见 `EpubUtil`），删掉之后那一行就空了；
     * 留着会渲染成一个只有段间距的空块，翻页时看着像页面上莫名多了一道空行。
     * <p>
     * 注意只丢弃"删完变空"的段落，**不对其它段落做 trim**——有些电子书用全角空格做首行缩进，
     * 统一 trim 会把正文的缩进风格改掉
     **/
    private static List<String> withoutImages(List<String> paragraphs) {
        List<String> result = new ArrayList<>(paragraphs.size());
        for (String text : paragraphs) {
            String stripped = IMG_MARKER.matcher(text).replaceAll("");
            if (stripped.trim().isEmpty()) {
                continue;
            }
            result.add(stripped);
        }
        return result;
    }

    private void addBlock(JComponent component) {
        add(component);
    }

    private static JComponent gap(int height) {
        JComponent strut = (JComponent) Box.createVerticalStrut(height);
        strut.setAlignmentX(Component.LEFT_ALIGNMENT);
        return strut;
    }

    /**
     * 助手话术：小字号、次要颜色，与正文区分开
     **/
    private JComponent statusLine(String text) {
        ParagraphPane pane = new ParagraphPane();
        pane.setWrapWidth(laidOutWidth);
        renderRuns(pane, text, AssistantTheme.uiFont(Math.max(11, bodyFont.getSize() - 1)),
                AssistantTheme.MUTED, false);
        return pane;
    }

    /**
     * 正文段落：使用设置页配置的字体与字号
     **/
    private JComponent paragraph(String text) {
        ParagraphPane pane = new ParagraphPane();
        pane.setWrapWidth(laidOutWidth);
        renderRuns(pane, text, bodyFont, AssistantTheme.TEXT, true);
        return pane;
    }

    /**
     * 小标题（模拟 Markdown 二级标题）
     **/
    private JComponent heading(String text) {
        AssistantTheme.SelectableText label = new AssistantTheme.SelectableText(text,
                AssistantTheme.bold(bodyFont.deriveFont((float) bodyFont.getSize() + 2)),
                AssistantTheme.TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * 单行 shell 命令块：头部结构与 diff 卡片完全一致（角标 + 文件名 + 复制/导出/刷新 + 主按钮），
     * 只是角标为 `SH`、文件名是 `scripts/xxx.sh`、主按钮是 "Run"（播放三角）而不是 "Apply"；
     * 正文只有一行 `$ 命令`。
     * <p>
     * 它专门用来插在自然段中间把段落切成两半，所以正文刻意保持一行——块本身比 diff 卡片"轻"
     **/
    private JComponent shellBlock(DisguiseContent.ShellScript script, int index) {
        FileNameLabel name = new FileNameLabel(script.fileName);
        name.setIcon(AssistantIcons.fileBadge("SH", AssistantTheme.CHIP_BG,
                AssistantTheme.CHIP_TEXT, JBUI.scale(17)));
        name.setIconTextGap(JBUI.scale(7));
        name.setFont(AssistantTheme.mono(Math.max(10, bodyFont.getSize() - 2)));
        name.setForeground(AssistantTheme.TEXT);

        JComponent toolbar = cardToolbar(script.command, "Run",
                AssistantIcons.play(JBUI.scale(11), AssistantTheme.ACCENT),
                "在终端运行", DisguiseContent.TOAST_RUNNING);

        // 与 diff 卡片同样的收窄策略：窄宽度下文件名不能和右上角工具栏叠在一起
        AssistantTheme.RoundedPanel card = new AssistantTheme.RoundedPanel(new BorderLayout(),
                8, AssistantTheme.CARD_BG, AssistantTheme.CARD_BORDER) {
            @Override
            public void doLayout() {
                layoutCardHeader(this, name, toolbar);
                super.doLayout();
            }
        };
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        header.setOpaque(true);
        header.setBackground(AssistantTheme.CARD_HEADER_BG);
        header.setBorder(JBUI.Borders.empty(5, 10, 5, 8));
        header.add(name, BorderLayout.WEST);
        header.add(toolbar, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);
        card.add(shellBody(script), BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(card, BorderLayout.CENTER);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height));
        wrapper.putClientProperty("thief.shellIndex", index);
        return wrapper;
    }

    /**
     * shell 块的正文：一行 `$ 命令`，提示符用强调色
     **/
    private JComponent shellBody(DisguiseContent.ShellScript script) {
        Font commandFont = AssistantTheme.monoSmall(bodyFont.getSize());

        AssistantTheme.SelectableText prompt = new AssistantTheme.SelectableText("$",
                commandFont, AssistantTheme.SHELL_PROMPT);

        AssistantTheme.SelectableText command = new AssistantTheme.SelectableText(script.command,
                commandFont, AssistantTheme.CODE_TEXT);

        JPanel row = new JPanel(new BorderLayout(JBUI.scale(7), 0));
        row.setOpaque(false);
        row.add(prompt, BorderLayout.WEST);
        row.add(command, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(JBUI.Borders.empty(5, JBUI.scale(12), 6, JBUI.scale(8)));
        body.add(row, BorderLayout.CENTER);
        return body;
    }

    /**
     * 挑出"可以插一条 shell 命令"的段落序号。
     * <p>
     * 只挑足够长、中间找得到句末标点、且不含 epub 图片的段落；再在候选里均匀取最多
     * {@link #MAX_SHELL_BLOCKS} 个。落点只由正文本身决定（同页稳定，翻页自然变化），
     * 不做随机，否则老板键恢复时同一页会长得不一样
     **/
    private static List<Integer> shellSlots(List<String> paragraphs, int seed) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i);
            if (text.length() < MIN_SHELL_PARAGRAPH || IMG_MARKER.matcher(text).find()) {
                continue;
            }
            if (splitPoint(text) > 0) {
                candidates.add(i);
            }
        }
        int take = Math.min(MAX_SHELL_BLOCKS, candidates.size());
        List<Integer> slots = new ArrayList<>(take);
        if (take == 0) {
            return slots;
        }
        int stride = Math.max(1, candidates.size() / take);
        int offset = Math.floorMod(seed, candidates.size());
        for (int k = 0; k < take; k++) {
            slots.add(candidates.get((offset + k * stride) % candidates.size()));
        }
        return slots;
    }

    /**
     * 在自然段"中间偏两侧"找一个句末标点，返回切开位置（切点在该标点之后）。
     * <p>
     * 只在段长的 35%~65% 区间里找，而且必须落在句末标点之后——宁可这一段不插命令，
     * 也不要在一句话中间硬断开（那一眼就假了）。找不到返回 -1
     **/
    private static int splitPoint(String text) {
        int length = text.length();
        int from = Math.max(1, (int) (length * 0.35));
        int to = Math.min(length - 1, (int) (length * 0.65));
        int middle = length / 2;
        int best = -1;
        for (int i = from; i <= to; i++) {
            if (!isSentenceEnd(text.charAt(i - 1))) {
                continue;
            }
            if (best < 0 || Math.abs(i - middle) < Math.abs(best - middle)) {
                best = i;
            }
        }
        return best;
    }

    /**
     * 判断段内插卡是否适用：段落从 {@code offset} 起的剩余部分跨越了下一个字数阈值
     * （consumed+offset 还没到、到段尾就到了），且剩余够长、还有卡片额度。
     * 返回切点（段内绝对偏移），不适用返回 -1
     **/
    private static int cardSplitCut(String text, int offset, int consumed, int placed, int extraCards, int totalChars) {
        if (placed >= extraCards || text.length() - offset < MIN_CARD_SPLIT_PARAGRAPH) {
            return -1;
        }
        double threshold = totalChars * (placed + 1) / (double) (extraCards + 1);
        int consumedBefore = consumed + offset;
        int consumedAfter = consumed + text.length();
        if (!(consumedBefore < threshold && consumedAfter >= threshold)) {
            return -1;
        }
        return cardSplitPoint(text, offset, (int) Math.round(threshold - consumedBefore));
    }

    /**
     * 段内插卡的切点：在目标位置（字数阈值落在段内的偏移，相对 {@code offset}）前后
     * {@link #CARD_SPLIT_BAND} 字的窗口里，找离它最近的句末标点（切点在该标点之后）。
     * 切点距已输出部分/段尾至少 {@link #CARD_SPLIT_EDGE} 字，保证每一段都还是"成段的正文"；
     * 找不到返回 -1（退回段后插卡）。
     * <p>
     * 注意 target 是相对 offset 的偏移，搜索窗口必须先换算成段内绝对位置——
     * 连续多刀时 offset 会推进，直接拿 target 当绝对下标会让切点越切越靠前
     **/
    private static int cardSplitPoint(String text, int offset, int target) {
        int length = text.length();
        int absTarget = offset + target;
        int from = Math.max(offset + CARD_SPLIT_EDGE, absTarget - CARD_SPLIT_BAND);
        int to = Math.min(length - CARD_SPLIT_EDGE, absTarget + CARD_SPLIT_BAND);
        int best = -1;
        for (int i = from; i <= to; i++) {
            if (isSentenceEnd(text.charAt(i - 1))
                    && (best < 0 || Math.abs(i - absTarget) < Math.abs(best - absTarget))) {
                best = i;
            }
        }
        return best;
    }

    /**
     * 句末标点（shell 块切段与段内插卡共用）：句号 / 叹号 / 问号 / 分号 / 省略号
     **/
    private static boolean isSentenceEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；' || c == '…';
    }

    /**
     * 把一段文本写进文档：epub 图片占位替换成图片，正文里的英文/数字渲染成行内代码
     **/
    private void renderRuns(JTextPane pane, String text, Font font, Color color, boolean allowInlineCode) {
        StyledDocument doc = pane.getStyledDocument();
        try {
            Matcher marker = IMG_MARKER.matcher(text);
            int last = 0;
            while (marker.find()) {
                if (marker.start() > last) {
                    appendText(doc, text.substring(last, marker.start()), font, color, allowInlineCode);
                }
                Icon icon = imageLoader == null ? null : imageLoader.apply(marker.group(1));
                if (icon != null) {
                    pane.setCaretPosition(doc.getLength());
                    pane.insertIcon(icon);
                }
                last = marker.end();
            }
            if (last < text.length()) {
                appendText(doc, text.substring(last), font, color, allowInlineCode);
            }
        } catch (BadLocationException e) {
            pane.setText(text);
        }
    }

    /**
     * 写入一段普通文本，其中的英文/数字会被切成行内代码块
     **/
    private void appendText(StyledDocument doc, String text, Font font, Color color, boolean allowInlineCode)
            throws BadLocationException {
        if (!allowInlineCode) {
            insertRun(doc, text, font, color, null);
            return;
        }
        Matcher matcher = INLINE_CODE.matcher(text);
        int last = 0;
        int count = 0;
        while (matcher.find() && count < MAX_INLINE_CODE) {
            if (matcher.start() > last) {
                insertRun(doc, text.substring(last, matcher.start()), font, color, null);
            }
            Font codeFont = AssistantTheme.mono(Math.max(10, font.getSize() - 1));
            insertRun(doc, matcher.group(), codeFont, AssistantTheme.CHIP_TEXT, AssistantTheme.CHIP_BG);
            last = matcher.end();
            count++;
        }
        if (last < text.length()) {
            insertRun(doc, text.substring(last), font, color, null);
        }
    }

    private void insertRun(StyledDocument doc, String text, Font font, Color color, Color background)
            throws BadLocationException {
        if (text.isEmpty()) {
            return;
        }
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, font.getFamily());
        StyleConstants.setFontSize(attrs, font.getSize());
        if (color != null) {
            StyleConstants.setForeground(attrs, color);
        }
        if (background != null) {
            StyleConstants.setBackground(attrs, background);
        }
        doc.insertString(doc.getLength(), text, attrs);
    }

    /**
     * 伪造的代码 diff 卡片
     **/
    private JComponent codeCard(DisguiseContent.Snippet snippet, int index) {
        FileNameLabel name = new FileNameLabel(snippet.fileName);
        name.setIcon(AssistantIcons.fileBadge(snippet.badge, AssistantTheme.CHIP_BG,
                AssistantTheme.CHIP_TEXT, JBUI.scale(17)));
        name.setIconTextGap(JBUI.scale(7));
        name.setFont(AssistantTheme.mono(Math.max(10, bodyFont.getSize() - 2)));
        name.setForeground(AssistantTheme.TEXT);

        JComponent toolbar = cardToolbar(snippet.plainText(), "Apply",
                AssistantIcons.check(JBUI.scale(13), AssistantTheme.ACCENT),
                "应用到工作区", DisguiseContent.TOAST_APPLIED);

        // 卡片较窄时（左侧"历史记录"展开后正文区可能只剩 250px）把文件名截成省略号，
        // 否则它会和右上角的工具栏图标叠在一起
        AssistantTheme.RoundedPanel card = new AssistantTheme.RoundedPanel(new BorderLayout(),
                8, AssistantTheme.CARD_BG, AssistantTheme.CARD_BORDER) {
            @Override
            public void doLayout() {
                layoutCardHeader(this, name, toolbar);
                super.doLayout();
            }
        };
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        header.setOpaque(true);
        header.setBackground(AssistantTheme.CARD_HEADER_BG);
        header.setBorder(JBUI.Borders.empty(5, 10, 5, 8));
        header.add(name, BorderLayout.WEST);
        header.add(toolbar, BorderLayout.EAST);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(JBUI.Borders.empty(5, 0, 6, 0));
        Font codeFont = AssistantTheme.monoSmall(bodyFont.getSize());
        for (DisguiseContent.CodeLine line : snippet.lines) {
            body.add(codeRow(line, codeFont));
        }

        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(card, BorderLayout.CENTER);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height));
        wrapper.putClientProperty("thief.cardIndex", index);
        return wrapper;
    }

    /**
     * 一行代码：新增行绿底、删除行红底、上下文行透明
     **/
    private JComponent codeRow(DisguiseContent.CodeLine line, Font codeFont) {
        Color background = null;
        if (line.kind == DisguiseContent.Kind.ADD) {
            background = AssistantTheme.ADD_BG;
        } else if (line.kind == DisguiseContent.Kind.DEL) {
            background = AssistantTheme.DEL_BG;
        }

        AssistantTheme.SelectableText label = new AssistantTheme.SelectableText(
                line.text.isEmpty() ? " " : line.text, codeFont, AssistantTheme.CODE_TEXT);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(background != null);
        if (background != null) {
            row.setBackground(background);
        }
        row.setBorder(JBUI.Borders.empty(1, JBUI.scale(12), 1, JBUI.scale(8)));
        row.add(label, BorderLayout.WEST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(row, BorderLayout.CENTER);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapper.getPreferredSize().height));
        return wrapper;
    }

    /**
     * 卡片右上角工具栏：复制 / 导出 / 重新生成 + 一个绿色主按钮（全部是伪装操作）。
     * <p>
     * 主按钮在 diff 卡片上是 "Apply"（应用到工作区），在 shell 块上是 "Run"（在终端运行）——
     * 图标、提示、反馈文案跟着换，这样两类卡片的头部结构一致，语义又不会混淆
     * <p>
     * 三个次要图标单独包成一组（{@link #MINOR_TOOLS}），卡片被挤窄时整组隐藏、只留主按钮，
     * 见 {@link #layoutCardHeader}
     **/
    private JComponent cardToolbar(String copyText, String primaryLabel, Icon primaryIcon,
                                   String primaryTooltip, String primaryToast) {
        JPanel minor = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0));
        minor.setOpaque(false);
        minor.add(iconButton(AssistantIcons.copy(JBUI.scale(15), AssistantTheme.MUTED), "复制代码",
                () -> {
                    CopyPasteManager.getInstance().setContents(new StringSelection(copyText));
                    toast(DisguiseContent.TOAST_COPIED);
                }));
        minor.add(iconButton(AssistantIcons.download(JBUI.scale(15), AssistantTheme.MUTED), "导出到工作区",
                () -> toast(DisguiseContent.TOAST_APPLIED)));
        minor.add(iconButton(AssistantIcons.refresh(JBUI.scale(15), AssistantTheme.MUTED), "重新生成",
                () -> toast(DisguiseContent.TOAST_THINKING)));

        AssistantTheme.GlyphButton primary = new AssistantTheme.GlyphButton(primaryLabel,
                AssistantTheme.uiFont(Math.max(10, bodyFont.getSize() - 3)));
        primary.setIcon(primaryIcon);
        primary.setIconTextGap(JBUI.scale(4));
        primary.setForeground(AssistantTheme.ACCENT);
        primary.baseBackground(AssistantTheme.ADD_BG).hoverBackground(AssistantTheme.ADD_BG_HOVER);
        primary.setBorder(JBUI.Borders.empty(3, 9, 3, 9));
        primary.setToolTipText(primaryTooltip);
        primary.addActionListener(e -> toast(primaryToast));

        return new CardToolbar(minor, primary);
    }

    /**
     * 卡片右上角工具栏：次要图标组（复制/导出/刷新）+ 主按钮（Apply / Run）。
     * <p>
     * 两个宽度在构造时就定死缓存下来——`FlowLayout` 的 preferredSize 会跳过不可见组件，
     * 把次要图标收起来之后 {@code getPreferredSize()} 就不再是完整宽度；若拿它当判据，
     * 会出现"收起 → 宽度变小 → 判定放得下 → 又展开 → 叠字"的来回抖动
     **/
    private static final class CardToolbar extends JPanel {
        final JComponent minor;
        final int fullWidth;
        final int primaryWidth;

        CardToolbar(JComponent minor, JComponent primary) {
            super(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0));
            setOpaque(false);
            this.minor = minor;
            add(minor);
            add(primary);
            int gap = ((FlowLayout) getLayout()).getHgap();
            this.primaryWidth = primary.getPreferredSize().width;
            this.fullWidth = minor.getPreferredSize().width + gap + this.primaryWidth;
        }
    }

    /**
     * 卡片头部的自适应布局（diff 卡片与 shell 块共用）。
     * <p>
     * 头部是 BorderLayout（左文件名 / 右工具栏），宽度不够时两边会直接叠在一起，所以分三级降级：
     * <ol>
     *   <li>先按可用宽度收窄文件名（丢目录前缀 → 尾部省略号）；</li>
     *   <li>还放不下就隐藏三个次要图标，只留主按钮（Apply / Run）；</li>
     *   <li>连主按钮都放不下（工具窗口被拖到极窄）就整条工具栏隐藏，头部只剩角标与文件名。</li>
     * </ol>
     * 阈值里的 {@code JBUI.scale(26)} 是头部左右内边距与角标宽度的合并余量
     **/
    private void layoutCardHeader(JComponent card, FileNameLabel name, JComponent toolbar) {
        int width = card.getWidth();
        if (width <= 0) {
            return;
        }
        int badge = JBUI.scale(26) + name.getIconTextGap()
                + (name.getIcon() == null ? 0 : name.getIcon().getIconWidth());
        int nameMin = JBUI.scale(40);

        int barWidth;
        if (toolbar instanceof CardToolbar) {
            CardToolbar bar = (CardToolbar) toolbar;
            boolean hideMinor = width < badge + nameMin + bar.fullWidth;
            boolean hideBar = width < badge + nameMin + bar.primaryWidth;
            bar.minor.setVisible(!hideMinor && !hideBar);
            bar.setVisible(!hideBar);
            barWidth = hideBar ? 0 : (hideMinor ? bar.primaryWidth : bar.fullWidth);
        } else {
            barWidth = toolbar.getPreferredSize().width;
        }
        name.fitTo(width - badge - barWidth);
    }

    private JComponent iconButton(Icon icon, String tooltip, Runnable action) {
        AssistantTheme.GlyphButton button = new AssistantTheme.GlyphButton(icon);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(JBUI.scale(22), JBUI.scale(22)));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void toast(String message) {
        if (onToast != null) {
            onToast.accept(message);
        }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return JBUI.scale(18);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(JBUI.scale(40), visibleRect.height - JBUI.scale(30));
    }

    /**
     * 宽度跟随视口：文本才会按窗口宽度换行，而不是撑出横向滚动条
     **/
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /**
     * 代码卡片头部的文件名标签：可用宽度不够时截断成省略号。
     * 卡片头部是 BorderLayout（左文件名 / 右工具栏），宽度不够时两边的组件会直接重叠，
     * 所以必须在布局阶段主动收窄文件名
     **/
    private static final class FileNameLabel extends JLabel {
        private final String fullText;

        FileNameLabel(String text) {
            super(text);
            this.fullText = text;
        }

        void fitTo(int available) {
            FontMetrics metrics = getFontMetrics(getFont());
            if (metrics == null) {
                return;
            }
            // 可用宽度算成负数时要主动清空：直接 return 会让文件名原样留着，
            // 和右上角工具栏叠在一起（左侧"历史记录"展开 + 工具窗口被拖窄时就会出现）
            if (available <= 0) {
                if (!getText().isEmpty()) {
                    setText("");
                }
                return;
            }
            String target = fullText;
            if (metrics.stringWidth(target) > available) {
                // 先丢掉目录前缀（"models/DSHGCN.py" → "DSHGCN.py"），文件名本身的信息量更大；
                // 还是放不下再从尾部截断
                int slash = fullText.lastIndexOf('/');
                String base = slash >= 0 ? fullText.substring(slash + 1) : fullText;
                target = metrics.stringWidth(base) <= available ? base : truncate(base, available, metrics);
            }
            // 文本没变就不要 setText：否则每轮布局都会重新校验，来回抖动
            if (!target.equals(getText())) {
                setText(target);
            }
        }

        private String truncate(String text, int available, FontMetrics metrics) {
            String ellipsis = "…";
            int end = text.length();
            while (end > 0 && metrics.stringWidth(text.substring(0, end))
                    + metrics.stringWidth(ellipsis) > available) {
                end--;
            }
            return end > 0 ? text.substring(0, end) + ellipsis : ellipsis;
        }
    }

    /**
     * 段落文本面板：在 BoxLayout 中需要自己按已知宽度算换行高度。
     * <p>
     * 正文是只读的，但要能选中复制（见 {@link AssistantTheme#makeSelectable}）——"不可编辑"
     * 和"不可选中"是两回事，这里不设 {@code setFocusable(false)}，否则鼠标选不中任何文字
     **/
    private final class ParagraphPane extends JTextPane {
        private int wrapWidth = -1;
        private int computedWidth = -1;

        ParagraphPane() {
            setOpaque(false);
            setEditable(false);
            setBorder(JBUI.Borders.empty());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            AssistantTheme.makeSelectable(this);
        }

        void setWrapWidth(int width) {
            if (width > 0 && width != wrapWidth) {
                wrapWidth = width;
                revalidate();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int width = wrapWidth > 0 ? wrapWidth : getWidth();
            if (width > 0 && width != computedWidth) {
                computedWidth = width;
                setSize(width, Integer.MAX_VALUE);
            }
            Dimension preferred = super.getPreferredSize();
            return new Dimension(width > 0 ? width : preferred.width, preferred.height);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public float getAlignmentX() {
            return Component.LEFT_ALIGNMENT;
        }
    }
}
