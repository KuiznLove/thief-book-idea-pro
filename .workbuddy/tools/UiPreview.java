import com.intellij.util.ui.JBUI;
import com.thief.idea.disguise.DisguiseContent;
import com.thief.idea.ui.AssistantIcons;
import com.thief.idea.ui.AssistantPageView;
import com.thief.idea.ui.AssistantTheme;
import com.thief.idea.ui.ChatInputBar;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * 独立 UI 预览：不启动 IDE，直接把伪装后的阅读面板渲染成 PNG，用来肉眼校对排版。
 * <p>
 * 一次输出 10 张图：
 * <ul>
 *   <li>{@code <输出名>.png} 左侧"历史记录"展开</li>
 *   <li>{@code <输出名>-collapsed.png} 左侧已收起</li>
 *   <li>{@code <输出名>-long.png} 长页样本，核对代码卡片分布</li>
 *   <li>{@code <输出名>-java.png} / {@code <输出名>-vue.png} 另两套素材的文件角标与代码风格</li>
 *   <li>{@code <输出名>-model.png} 超长自定义模型名，核对输入框右下角不会把输入区挤变形</li>
 *   <li>{@code <输出名>-noshell.png} 关掉 "Shell snippet" 开关的样子（和第一张对比看单行 shell 块有没有消失）</li>
 *   <li>{@code <输出名>-narrow.png} 窄宽度（400px + 侧栏展开），核对卡片头部三级降级不失效</li>
 *   <li>{@code <输出名>-image.png} / {@code <输出名>-noimage.png} 带插图的样本，核对图片渲染与 "Hide book images" 无图模式</li>
 * </ul>
 * <p>
 * 运行（需要 IntelliJ Platform 的 jar 在 classpath 上）：
 * java -Dfile.encoding=UTF-8 -cp "<platform>/lib/*;build/classes/java/main" UiPreview.java out.png [宽 高]
 * <p>
 * <b>-Dfile.encoding=UTF-8 不能省</b>：本文件是 UTF-8，单文件源码启动时 javac 按平台默认编码
 * （中文 Windows 上是 GBK）解码源文件，中文会变乱码，字符串里的 \n 转义还会被错位的双字节序列吃掉。
 **/
public class UiPreview {

    private static final String SAMPLE = "话说天下大势，分久必合，合久必分。\n"
            + "周末七国分争，并入于秦。及秦灭之后，楚、汉分争，又并入于汉。\n"
            + "汉朝自高祖斩白蛇而起义，一统天下，后来光武中兴，传至献帝，遂分为三国。\n"
            + "推其致乱之由，殆始于桓、灵二帝。\n"
            + "桓帝禁锢善类，崇信宦官。及桓帝崩，灵帝即位，大将军窦武、太傅陈蕃共相辅佐。\n"
            + "时有宦官曹节等弄权，窦武、陈蕃谋诛之，机事不密，反为所害，中涓自此愈横。\n"
            + "建宁二年四月望日，帝御温德殿。方升座，殿角狂风骤起。\n"
            + "（本卷据中华书局 1959 年校勘本，参校 2 个版本，共 120 回，ISBN 978-7-101-00347-4。）\n"
            + "只见一条大青蛇，从梁上飞将下来，蟠于椅上。帝惊倒，左右急救入宫，百官俱奔避。\n"
            + "须臾，蛇不见了。忽然大雷大雨，加以冰雹，落到半夜方止，坏却房屋无数。\n"
            + "建宁四年二月，洛阳地震；又海水泛溢，沿海居民，尽被大浪卷入海中。\n"
            + "光和元年，雌鸡化雄。六月朔，黑气十余丈，飞入温德殿中。秋七月，有虹见于玉堂。\n"
            + "种种不祥，非止一端。帝下诏问群臣以灾异之由，议郎蔡邕上疏，以为蜺堕鸡化，乃妇寺干政之所致。\n"
            + "言颇切直，帝览奏叹息，因起更衣。曹节在后窃视，悉宣告左右。\n"
            + "遂以他事陷邕于罪，放归田里。后张让、赵忠、封谞、段珪等十人朋比为奸，号为“十常侍”。\n"
            + "（本卷据中华书局 1959 年校勘本，参校 2 个版本，共 120 回。）";

    /**
     * 长页样本（约 1300 字、25 段）：用来验证"代码卡片按字数均匀分布"，
     * 也就是用户反馈的"一整页都是正文、半天看不到代码块"那种页面。
     **/
    private static final String LONG_SAMPLE = "且说张角一军，前犯幽州界分。幽州太守刘焉，乃江夏竟陵人氏，汉鲁恭王之后也。\n"
            + "当时闻得贼兵将至，召校尉邹靖计议。靖曰：“贼兵众，我兵寡，明公宜作速招军应敌。”\n"
            + "刘焉然其说，随即出榜招募义兵。榜文行到涿县，引出涿县中一个英雄。\n"
            + "那人不甚好读书；性宽和，寡言语，喜怒不形于色；素有大志，专好结交天下豪杰。\n"
            + "玄德看毕榜文，慨然长叹。随后一人厉声言曰：“大丈夫不与国家出力，何故长叹？”\n"
            + "玄德回视其人，身长八尺，豹头环眼，燕颔虎须，声若巨雷，势如奔马。\n"
            + "玄德见他形貌异常，问其姓名。其人曰：“某姓张名飞，字翼德。世居涿郡，颇有庄田，卖酒屠猪，专好结交天下豪杰。”\n"
            + "恰才见公看榜而叹，故此相问。玄德曰：“我本汉室宗亲，姓刘，名备。今闻黄巾倡乱，有志欲破贼安民，恨力不能，故长叹耳。”\n"
            + "飞曰：“吾颇有资财，当招募乡勇，与公同举大事，如何？”玄德甚喜，遂与同入村店中饮酒。\n"
            + "正饮间，见一大汉，推着一辆车子，到店门首歇了，入店坐下，便唤酒保：“快斟酒来吃，我待赶入城去投军。”\n"
            + "玄德看其人：身长九尺，髯长二尺；面如重枣，唇若涂脂；丹凤眼，卧蚕眉，相貌堂堂，威风凛凛。\n"
            + "玄德就邀他同坐，叩其姓名。其人曰：“吾姓关名羽，字长生，后改云长，河东解良人也。”\n"
            + "因本处势豪倚势凌人，被吾杀了，逃难江湖，五六年矣。今闻此处招军破贼，特来应募。\n"
            + "玄德遂以己志告之，云长大喜。同到张飞庄上，共议大事。\n"
            + "飞曰：“吾庄后有一桃园，花开正盛；明日当于园中祭告天地，我三人结为兄弟，协力同心，然后可图大事。”\n"
            + "次日，于桃园中，备下乌牛白马祭礼等项，三人焚香再拜而说誓曰：\n"
            + "“念刘备、关羽、张飞，虽然异姓，既结为兄弟，则同心协力，救困扶危；上报国家，下安黎庶。\n"
            + "不求同年同月同日生，只愿同年同月同日死。皇天后土，实鉴此心，背义忘恩，天人共戮！”\n"
            + "誓毕，拜玄德为兄，关羽次之，张飞为弟。祭罢天地，复宰牛设酒，聚乡中勇士，得三百余人，就桃园中痛饮一醉。\n"
            + "来日收拾军器，但恨无马匹可乘。正思虑间，人报有两个客人，引一伙伴当，赶一群马，投庄上来。\n"
            + "玄德曰：“此天佑我也！”三人出庄迎接。原来二客乃中山大商：一名张世平，一名苏双，每年往北贩马，近因寇发而回。\n"
            + "玄德请二人到庄，置酒管待，诉说欲讨贼安民之意。二客大喜，愿将良马五十匹相送；又赠金银五百两，镔铁一千斤，以资器用。\n"
            + "玄德谢别二客，便命良匠打造双股剑。云长造青龙偃月刀，又名“冷艳锯”，重八十二斤。\n"
            + "张飞造丈八点钢矛。各置全身铠甲。共聚乡勇五百余人，来见邹靖。\n"
            + "邹靖引见太守刘焉。三人参见毕，各通姓名。玄德说起宗派，刘焉大喜，遂认玄德为侄。";

    /**
     * 带插图的样本：epub 解包时插图是**单独一行**的 {@code [[IMG:文件名]]} 占位（见 EpubUtil），
     * 用来验证插图渲染与"无图模式"（设置页 "Hide book images"）。
     * 三处占位都独占一行，正好也覆盖"整段只有插图"这种要被整段丢弃的情况
     **/
    private static final String IMAGE_SAMPLE = "话说天下大势，分久必合，合久必分。\n"
            + "周末七国分争，并入于秦。及秦灭之后，楚、汉分争，又并入于汉。\n"
            + "[[IMG:figure-01.png]]\n"
            + "汉朝自高祖斩白蛇而起义，一统天下，后来光武中兴，传至献帝，遂分为三国。\n"
            + "推其致乱之由，殆始于桓、灵二帝。桓帝禁锢善类，崇信宦官。\n"
            + "[[IMG:figure-02.png]]\n"
            + "建宁二年四月望日，帝御温德殿。方升座，殿角狂风骤起。\n"
            + "只见一条大青蛇，从梁上飞将下来，蟠于椅上。帝惊倒，左右急救入宫，百官俱奔避。\n"
            + "[[IMG:figure-03.png]]\n"
            + "须臾，蛇不见了。忽然大雷大雨，加以冰雹，落到半夜方止，坏却房屋无数。\n"
            + "建宁四年二月，洛阳地震；又海水泛溢，沿海居民，尽被大浪卷入海中。\n";

    /**
     * 模拟 epub 目录（界面上伪装成"历史记录"）
     **/
    private static final List<String> TOC = Arrays.asList(
            "第一回 宴桃园豪杰三结义", "第二回 张翼德怒鞭督邮", "第三回 议温明董卓叱丁原",
            "第四回 废汉帝陈留践位", "第五回 发矫诏诸镇应曹公", "第六回 焚金阙董卓行凶",
            "第七回 袁绍磐河战公孙", "第八回 王司徒巧使连环计", "第九回 除暴凶吕布助司徒",
            "第十回 勤王室马腾举义", "第十一回 刘皇叔北海救孔融", "第十二回 陶恭祖三让徐州");

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "preview.png";
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 560;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 900;

        // 独立运行时平台没走过启动流程，JBUI 的缩放因子需要手工预置，否则 JBUI.scale 会抛 "Must be precomputed"
        try {
            com.intellij.ui.scale.JBUIScale.setSystemScaleFactor(1f);
            com.intellij.ui.scale.JBUIScale.setUserScaleFactorForTest(1f);
        } catch (Throwable ignored) {
        }
        // 说明：这里只渲染亮色。切 DarculaLaf 会在没有 Application 的环境里卡住，
        // 暗色配色靠 AssistantTheme 里的 JBColor 亮/暗配对保证，改动配色时请自行核对暗色分支。

        // 先打印样本的"运行时长什么样"：本文件是 UTF-8，若启动时没带 -Dfile.encoding=UTF-8，
        // javac 会按平台默认编码（中文 Windows 是 GBK）解码源文件，中文不但变成乱码，
        // 连字符串里的 \n 转义都会被错位的双字节序列吃掉 —— 样本会被拆错，卡片数量也就跟着错。
        System.out.println("[sample] SAMPLE chars=" + SAMPLE.length()
                + " lines=" + SAMPLE.split("\n").length
                + " | LONG chars=" + LONG_SAMPLE.length()
                + " lines=" + LONG_SAMPLE.split("\n").length);

        render(out, width, height, true, SAMPLE, DisguiseContent.PYTHON);
        render(collapsedName(out), width, height, false, SAMPLE, DisguiseContent.PYTHON);
        // 长页样本：画布加高，用来核对代码卡片在长页里的分布密度
        render(longName(out), width, Math.max(height, 2200), true, LONG_SAMPLE, DisguiseContent.PYTHON);
        // 另外两套素材：核对文件角标与代码风格
        render(suffix(out, "-java.png"), width, height, true, SAMPLE, DisguiseContent.JAVA);
        render(suffix(out, "-vue.png"), width, height, true, SAMPLE, DisguiseContent.VUE);
        // 自定义模型名（设置页 "Custom model"）：故意用超长名字，检查输入框右下角会不会把输入区挤变形
        render(suffix(out, "-model.png"), width, height, true, SAMPLE, DisguiseContent.JAVA,
                "My-Local-Qwen3-Coder-Plus-32B-Instruct");
        // 关掉 "Shell snippet" 开关，画布与 preview-long.png 一致，方便两张对照
        // 看单行 shell 块是不是真的消失了
        render(suffix(out, "-noshell.png"), width, Math.max(height, 2200), true, LONG_SAMPLE,
                DisguiseContent.PYTHON, DisguiseContent.MODELS[0], false, true, null);
        // 窄宽度：固定 400px + 侧栏展开，正文区只剩约 174px，专门用来核对卡片头部的
        // 三级降级（收窄文件名 → 收起次要图标 → 收起整条工具栏）有没有失效、会不会叠字。
        // 用固定宽度而不是跟随参数，是为了让它每次预览都被覆盖到，不依赖手输参数
        render(suffix(out, "-narrow.png"), 400, height, true, SAMPLE, DisguiseContent.PYTHON);
        // 可选：第 4 个参数给一个本地 txt 样本（如用户书里截出来的一页），
        // 用长页画布额外渲染一张，方便按真实正文核对卡片/shell 分布
        if (args.length > 3) {
            String fileSample = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(args[3])), java.nio.charset.StandardCharsets.UTF_8);
            render(suffix(out, "-file.png"), width, Math.max(height, 2200), true, fileSample,
                    DisguiseContent.PYTHON);
        }
        System.exit(0);
    }

    private static String collapsedName(String out) {
        return suffix(out, "-collapsed.png");
    }

    private static String longName(String out) {
        return suffix(out, "-long.png");
    }

    private static String suffix(String out, String suffix) {
        int dot = out.lastIndexOf('.');
        return dot > 0 ? out.substring(0, dot) + suffix : out + suffix;
    }

    private static void render(String out, int width, int height, boolean sidebarExpanded,
                               String sample, String language) throws Exception {
        render(out, width, height, sidebarExpanded, sample, language, DisguiseContent.MODELS[0],
                true, true, null);
    }

    private static void render(String out, int width, int height, boolean sidebarExpanded,
                               String sample, String language, String modelName) throws Exception {
        render(out, width, height, sidebarExpanded, sample, language, modelName, true, true, null);
    }

    /**
     * @param modelName 输入框右下角显示的模型名（= 设置页里可自定义的那个，纯伪装）
     * @param shellEnabled 是否插入单行 shell 代码块（设置页 "Shell snippet" 开关）
     **/
    /**
     * @param modelName 输入框右下角显示的模型名（= 设置页里可自定义的那个，纯伪装）
     * @param shellEnabled 是否插入单行 shell 代码块（设置页 "Shell snippet" 开关）
     * @param imageEnabled 是否渲染插图（设置页 "Hide book images" 的反值）
     * @param iconLoader 图片加载器：给个返回合成占位图标的实现，就能在离线预览里看到插图
     **/
    private static void render(String out, int width, int height, boolean sidebarExpanded,
                               String sample, String language, String modelName, boolean shellEnabled,
                               boolean imageEnabled, Function<String, Icon> iconLoader)
            throws Exception {
        AssistantPageView page = new AssistantPageView(message -> {
        }, iconLoader);
        page.render(sample, 123456, AssistantTheme.uiFont(14), JBUI.scale(7), language,
                shellEnabled, imageEnabled);

        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        if (sidebarExpanded) {
            center.add(tocPanel(), BorderLayout.WEST);
        }
        center.add(inputBar(modelName), BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(headerBar(sidebarExpanded), BorderLayout.NORTH);
        body.add(center, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AssistantTheme.panelBackground());
        root.add(body, BorderLayout.CENTER);

        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setContentPane(root);
        frame.setSize(width, height);
        frame.setLocation(60, 60);
        frame.setVisible(true);

        Thread.sleep(1300);
        if (sample == LONG_SAMPLE
                || (sample == SAMPLE && sidebarExpanded && DisguiseContent.PYTHON.equals(language))) {
            reportDistribution(page);
        }
        BufferedImage image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(2, 2);
        root.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", new File(out));
        frame.dispose();
        System.out.println("preview written: " + new File(out).getAbsolutePath());
    }

    /**
     * 打印代码卡片与单行 shell 块在整页里的纵向分布，用来定量核对"有没有均匀铺开"
     **/
    private static void reportDistribution(AssistantPageView page) {
        int total = page.getHeight();
        int cards = 0;
        int shells = 0;
        System.out.println("[distribution] page height = " + total + "px");
        for (Component component : page.getComponents()) {
            if (!(component instanceof JComponent)) {
                continue;
            }
            JComponent jc = (JComponent) component;
            Object cardIndex = jc.getClientProperty("thief.cardIndex");
            Object shellIndex = jc.getClientProperty("thief.shellIndex");
            if (cardIndex == null && shellIndex == null) {
                continue;
            }
            int percent = Math.round(component.getY() * 100f / Math.max(1, total));
            if (cardIndex != null) {
                cards++;
                System.out.println("  card  #" + cardIndex + "  y=" + component.getY() + "px  (" + percent + "%)");
            } else {
                shells++;
                System.out.println("  shell #" + shellIndex + "  y=" + component.getY() + "px  (" + percent + "%)");
            }
        }
        System.out.println("[distribution] cards on page = " + cards + ", shell lines = " + shells
                + ", direct children = " + page.getComponentCount());
    }

    /**
     * 顶部伪装栏（与 MainUi.initHeaderBar 一致的外观：整条栏一行排开，RowLayout 共用中心线）
     **/
    private static JComponent headerBar(boolean sidebarExpanded) {
        JPanel bar = new JPanel(new AssistantTheme.RowLayout(JBUI.scale(8)));
        bar.setOpaque(false);
        bar.setBorder(JBUI.Borders.empty(7, JBUI.scale(14), 4, JBUI.scale(12)));

        JLabel brand = new JLabel("CodePilot");
        brand.setIcon(AssistantIcons.brand(JBUI.scale(17)));
        brand.setIconTextGap(JBUI.scale(7));
        brand.setFont(AssistantTheme.uiFont(12).deriveFont(Font.BOLD));
        brand.setForeground(AssistantTheme.TEXT);

        AssistantTheme.GlyphButton sidebar = new AssistantTheme.GlyphButton(
                AssistantIcons.sidebar(JBUI.scale(15), AssistantTheme.MUTED, sidebarExpanded));
        sidebar.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(22)));

        AssistantTheme.GlyphButton session = new AssistantTheme.GlyphButton("会话 1", AssistantTheme.uiFont(11));
        session.setIcon(AssistantIcons.chevronDown(JBUI.scale(13), AssistantTheme.MUTED));
        session.setHorizontalTextPosition(SwingConstants.LEFT);
        session.setIconTextGap(JBUI.scale(3));
        session.setBorder(JBUI.Borders.empty(2, JBUI.scale(6), 2, JBUI.scale(4)));

        JLabel pageLabel = new JLabel("18 / 934");
        pageLabel.setFont(AssistantTheme.uiFont(11));
        pageLabel.setForeground(AssistantTheme.MUTED);
        pageLabel.setBorder(JBUI.Borders.empty(0, 0, 0, JBUI.scale(6)));

        JComponent prev = iconButton(AssistantIcons.chevronLeft(JBUI.scale(15), AssistantTheme.MUTED));
        JComponent next = iconButton(AssistantIcons.chevronRight(JBUI.scale(15), AssistantTheme.MUTED));
        JComponent speaker = iconButton(AssistantIcons.speaker(JBUI.scale(15), AssistantTheme.MUTED, false));

        AssistantTheme.RowLayout.gapBefore(brand, JBUI.scale(8));
        bar.add(brand);
        bar.add(sidebar);
        bar.add(session);
        bar.add(AssistantTheme.RowLayout.spring());
        AssistantTheme.RowLayout.gapBefore(pageLabel, 0);
        bar.add(pageLabel);
        for (JComponent item : new JComponent[]{prev, next, speaker}) {
            AssistantTheme.RowLayout.gapBefore(item, JBUI.scale(2));
            bar.add(item);
        }
        return bar;
    }

    /**
     * 左侧 epub 目录面板（与 MainUi.initTocPanel 一致的外观）
     **/
    private static JComponent tocPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel title = new JLabel("历史记录");
        title.setFont(AssistantTheme.uiFont(11));
        title.setForeground(AssistantTheme.MUTED);
        title.setBorder(JBUI.Borders.empty(4, JBUI.scale(12), 4, 8));

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String item : TOC) {
            model.addElement(item);
        }
        JList<String> list = new JList<>(model);
        list.setOpaque(false);
        list.setFont(AssistantTheme.uiFont(11));
        list.setForeground(AssistantTheme.TEXT);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(3);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focused);
                label.setFont(AssistantTheme.uiFont(11));
                label.setBorder(JBUI.Borders.empty(2, JBUI.scale(8)));
                return label;
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        panel.add(title, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(JBUI.scale(190), 0));
        return panel;
    }

    private static JComponent iconButton(Icon icon) {
        AssistantTheme.GlyphButton button = new AssistantTheme.GlyphButton(icon);
        button.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(22)));
        return button;
    }

    private static JComponent inputBar(String modelName) {
        ChatInputBar bar = new ChatInputBar(new ChatInputBar.Listener() {
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
        }, modelName);
        // 自定义模型名同时进模型列表（点开菜单才看得到，这里主要是为了走一遍真实调用路径）
        bar.setCustomModel(modelName);
        return bar;
    }
}
