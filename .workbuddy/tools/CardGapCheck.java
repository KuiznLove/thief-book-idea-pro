import com.intellij.util.ui.JBUI;
import com.thief.idea.ui.AssistantPageView;
import com.thief.idea.ui.AssistantTheme;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码卡片分布自测：验证"卡片间隔不被长段落撑大"。
 * <p>
 * 背景：卡片按累计字数分布，但只落在段落之间时，跨越字数阈值的长段落会把间隔撑大
 * 到整段长度，出现满屏正文没有代码块的页面（用户报过）。段内插卡后，间隔超冲应被
 * 限制在一句话以内。
 * <p>
 * 做法：渲染若干最坏情况样本（超长段落 / 长短混合），遍历组件树统计相邻"代码元素"
 * （diff 卡片 / shell 块）之间的正文累计字数，断言最大间隔 ≤ 阈值间距 + 切点搜索窗口；
 * 同时断言同一 seed 渲染两次分布一致（老板键恢复时同页不能变样）。
 * <p>
 * java -Dfile.encoding=UTF-8 --add-exports=java.desktop/sun.font=ALL-UNNAMED \
 *   -cp "<platform>/lib/*;build/classes/java/main" CardGapCheck.java
 **/
public class CardGapCheck {

    /** 间隔上界：阈值间距最坏 ~500（总字数 4000 / 8）+ 切点窗口 ±75*2 + 边距 */
    private static final int MAX_GAP_CHARS = 680;

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        try {
            com.intellij.ui.scale.JBUIScale.setSystemScaleFactor(1f);
            com.intellij.ui.scale.JBUIScale.setUserScaleFactorForTest(1f);
        } catch (Throwable ignored) {
        }

        check("超长段落（每段 600 字 × 4 段）", paragraphs(4, 600));
        check("极端长段（2000 字一段 + 400 字一段）", paragraphs(1, 2000) + "\n" + paragraphs(1, 400));
        check("长短混合", mix());
        check("中等段落（每段 200 字 × 10 段）", paragraphs(10, 200));
        check("普通页面（每段 60 字 × 12 段）", paragraphs(12, 60));

        if (failures == 0) {
            System.out.println("ALL PASS");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    /** 生成 n 段、每段约 chars 字的中文正文（句子以句末标点结尾） */
    private static String paragraphs(int n, int chars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int written = 0;
            int sentence = 1;
            while (written < chars) {
                int len = 18 + (sentence * 7) % 23;
                for (int k = 0; k < len; k++) {
                    sb.append("天地玄黄宇宙洪荒日月盈昃".charAt((written + k) % 12));
                }
                sb.append("。");
                written += len + 1;
                sentence++;
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 长短混合：一段 800 + 短段若干 + 一段 700 */
    private static String mix() {
        return paragraphs(1, 800) + paragraphs(3, 60) + paragraphs(1, 700) + paragraphs(3, 45);
    }

    private static void check(String name, String sample) {
        List<Integer> gaps1 = cardGaps(render(sample, 424242, true));
        List<Integer> gaps2 = cardGaps(render(sample, 424242, true));
        System.out.println("== " + name + " | cards=" + (countCards(render(sample, 424242, true)) )
                + " | gaps=" + gaps1);
        int max = gaps1.stream().max(Integer::compareTo).orElse(0);
        if (max > MAX_GAP_CHARS) {
            failures++;
            System.out.println("   FAIL 最大间隔 " + max + " 字 > 上界 " + MAX_GAP_CHARS);
        }
        if (!gaps1.equals(gaps2)) {
            failures++;
            System.out.println("   FAIL 同 seed 两次渲染分布不一致: " + gaps1 + " vs " + gaps2);
        }
    }

    private static AssistantPageView render(String sample, int seed, boolean shellEnabled) {
        AssistantPageView page = new AssistantPageView(m -> {
        }, null);
        page.render(sample, seed, AssistantTheme.uiFont(14), JBUI.scale(7),
                "Python", shellEnabled, true);
        return page;
    }

    /** 相邻代码元素（diff 卡片 / shell 块）之间的正文累计字数；首个间隔从页面开头算 */
    private static List<Integer> cardGaps(AssistantPageView page) {
        List<Integer> gaps = new ArrayList<>();
        int chars = 0;
        for (Component c : page.getComponents()) {
            if (!(c instanceof JComponent)) {
                continue;
            }
            Object card = ((JComponent) c).getClientProperty("thief.cardIndex");
            Object shell = ((JComponent) c).getClientProperty("thief.shellIndex");
            if (card != null || shell != null) {
                gaps.add(chars);
                chars = 0;
                continue;
            }
            if (c instanceof JTextPane) {
                chars += ((JTextPane) c).getDocument().getLength();
            }
        }
        gaps.add(chars);
        return gaps;
    }

    private static int countCards(AssistantPageView page) {
        int cards = 0;
        for (Component c : page.getComponents()) {
            if (c instanceof JComponent
                    && ((JComponent) c).getClientProperty("thief.cardIndex") != null) {
                cards++;
            }
        }
        return cards;
    }
}
