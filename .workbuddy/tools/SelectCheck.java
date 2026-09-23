import com.intellij.ui.scale.JBUIScale;
import com.thief.idea.ui.AssistantPageView;
import com.thief.idea.ui.AssistantTheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 可选中/可复制自检：离线渲染一页伪装界面，然后遍历面板里所有文本组件，
 * 逐个尝试建立选区并读回选中文字。
 * <p>
 * 只读文本选不中的根因是组件不可获取焦点（{@code setFocusable(false)}），
 * 而这一点在截图里完全看不出来，所以单独跑这个检查。
 * <p>
 * 运行（JDK 25 / JBR，classpath 需要 IntelliJ Platform 与本项目 class）：
 * <pre>
 * jbr/bin/java.exe -Dfile.encoding=UTF-8 --add-exports=java.desktop/sun.font=ALL-UNNAMED \
 *   --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
 *   -cp "&lt;平台目录&gt;/lib/*;build/classes/java/main" .workbuddy/tools/SelectCheck.java [选中效果.png]
 * </pre>
 * 期望输出：文本组件总数 &gt; 0，可选中数 == 总数，不可选中数 == 0。
 * 给了输出路径时还会截一张"段落处于选中状态"的图——用来确认**隐藏插入符没有连选区高亮一起干掉**
 * （两者的绘制走 Caret 的不同重载，改这块时值得眼看一遍）。
 **/
public class SelectCheck {

    private static final String SAMPLE =
            "他推开门，风雪立刻灌了进来。屋里的火盆早就熄了，只剩下一层冷灰。"
                    + "少年把斗篷上的雪抖落，走到桌前坐下，从怀里摸出一块发黑的玉牌。\n"
                    + "灯芯噼啪响了一声。他盯着那块玉牌看了很久，忽然笑了：原来是这样。\n"
                    + "外面的风越刮越紧，窗户纸被吹得哗哗作响，像是有什么东西正在墙外来回走动。";

    public static void main(String[] args) throws Exception {
        JBUIScale.setSystemScaleFactor(1f);

        // 关掉图片渲染，避免依赖 epub 解包
        AssistantPageView view = new AssistantPageView(message -> {
        }, name -> null);
        view.setSize(560, 900);
        view.render(SAMPLE, 7, AssistantTheme.uiFont(14), 7, "Python", true, false);
        view.doLayout();

        List<JTextComponent> texts = new ArrayList<>();
        collect(view, texts);

        int selectable = 0;
        int failed = 0;
        System.out.println("[select-check] 文本组件 = " + texts.size());
        for (JTextComponent text : texts) {
            String kind = text.getClass().getSimpleName();
            boolean focusable = text.isFocusable();
            boolean editable = text.isEditable();
            String selected = null;
            String error = null;
            int length = text.getDocument().getLength();
            if (length > 0) {
                text.select(0, Math.min(3, length));
                try {
                    selected = text.getSelectedText();
                } catch (Exception e) {
                    error = e.toString();
                }
                text.select(0, 0);
            }
            // 空文档（例如代码里的空行占位）没有可复制的内容，只校验前两项
            boolean ok = focusable && !editable && (length == 0 || selected != null || error != null);
            if (ok) {
                selectable++;
            } else {
                failed++;
            }
            System.out.printf("[select-check] %-14s focusable=%-5s editable=%-5s selected=%s%s%n",
                    kind, focusable, editable,
                    selected == null ? "(null)" : "\"" + selected + "\"",
                    error == null ? "" : " error=" + error);
        }
        System.out.println("[select-check] 可选中 = " + selectable + "，不可选中 = " + failed);

        // 插入符不画（只读回复不该有闪烁光标）：覆盖的是 1 参 paint，
        // 这里确认它不会因为空实现而破坏 DefaultCaret 的状态
        for (JTextComponent text : texts) {
            if (text.getCaret() == null) {
                System.out.println("[select-check] 警告：存在没有 caret 的组件 " + text.getClass());
            }
        }

        if (args.length > 0) {
            shoot(view, texts, args[0]);
        }
        System.exit(failed == 0 && !texts.isEmpty() ? 0 : 1);
    }

    /**
     * 截一张"正文处于选中状态"的图：验证隐藏插入符之后选区高亮仍然绘制
     **/
    private static void shoot(AssistantPageView view, List<JTextComponent> texts, String out)
            throws Exception {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AssistantTheme.panelBackground());
        root.add(view, BorderLayout.CENTER);
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setContentPane(root);
        frame.setSize(560, 900);
        frame.setLocation(60, 60);
        frame.setVisible(true);

        int selected = 0;
        for (JTextComponent text : texts) {
            int length = text.getDocument().getLength();
            if (length >= 10) {
                text.select(0, Math.min(10, length));
                selected++;
            }
        }
        // 让被选中的第一个组件拿到焦点，看着和"用户拖选时"一致
        if (!texts.isEmpty()) {
            texts.get(0).requestFocusInWindow();
        }
        Thread.sleep(800);

        BufferedImage image = new BufferedImage(560 * 2, 900 * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(2, 2);
        root.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", new File(out));
        frame.dispose();
        System.out.println("[select-check] 已选中 " + selected + " 个组件，截图 " + out);
    }

    private static void collect(Container container, List<JTextComponent> out) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTextComponent) {
                out.add((JTextComponent) component);
            } else if (component instanceof Container) {
                collect((Container) component, out);
            }
        }
    }
}
