import com.thief.idea.book.BookPager;
import com.thief.idea.book.BookSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * 分页引擎的边界核对（PagerCheck 的补充），覆盖它没测的三种情形：
 * <ol>
 *   <li>首页继续"上一页"（UI 层有守卫 {@code currentLine()/lineCount <= 1} 兜着，
 *       但引擎自身没有下限保护）；</li>
 *   <li>越界 turnBack 之后再 jumpTo，定位是否仍然准确——这是"下限保护"真正要紧的地方：
 *       {@code readForwardLocked} 在 currentPage 回落到 0 时会写 {@code seekDictionary[0]}，
 *       如果此时文件指针已经在第 N 行之后，这个缓存项就被写成了错误偏移，
 *       后续 countSeekLocked 从缓存 0 起定位就会整体错页；</li>
 *   <li>CRLF 换行的行读取（\r 是否被剥掉）与 jumpTo 越界不崩。</li>
 * </ol>
 * <p>
 * 运行：java -Dfile.encoding=UTF-8 -cp "&lt;platform&gt;/lib/*;build/classes/java/main" .workbuddy/tools/PagerEdgeCheck.java
 * <p>
 * <b>当前有一条预期失败</b>（见下），是引擎 side 的隐患，不是回归——重构前的 MainUi.readBook
 * 里同样是 {@code if (currentPage % cacheInterval == 0) seekDictionary.put(currentPage, seek);}，
 * 只是当时 currentPage 由 EDT 直接维护、且 UI 守卫拦住了负值，所以没暴露。
 **/
public class PagerEdgeCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        File f = File.createTempFile("pageredge-", ".txt");
        f.deleteOnExit();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            sb.append("L").append(i).append('\n');
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }

        BookPager pager = new BookPager(new BookSource(f.getAbsolutePath()));
        pager.setLinesPerPage(10);
        check("countLines", pager.countLines(), 100);

        // —— 1. 首页继续上一页：内容必须仍是首页，页码不应变负 ——
        pager.setCurrentPage(0);
        pager.reloadCurrent();
        for (int k = 1; k <= 2; k++) {
            String page = pager.turnBack();
            System.out.println("  info  第" + k + "次 turnBack → currentPage=" + pager.currentPage()
                    + "（显示页号 " + displayPage(pager.currentPage(), 10) + "）");
            check("过界 turnBack 内容仍是首页(" + k + ")", firstLine(page), "L1");
        }
        check("过界 turnBack 后 currentPage 不为负", pager.currentPage() >= 0, true);

        // —— 2. 越界之后的 jumpTo 定位准确性（缓存污染检查）——
        pager.setCurrentPage(0);
        String after = pager.jumpTo(10);
        check("越界 turnBack 后 jumpTo(10) 首行", firstLine(after), "L11");

        // —— 3. jumpTo 越界与 CRLF ——
        pager.setCurrentPage(0);
        String far = pager.jumpTo(9999);
        check("jumpTo(9999) 返回空页", far.trim().isEmpty(), true);
        System.out.println("  info  jumpTo(9999) 后 currentPage=" + pager.currentPage()
                + "（MainUi.jumpToPage 会把越界页码夹到 totalLine-1，这里直接调引擎没夹）");

        File crlf = File.createTempFile("pageredge-crlf-", ".txt");
        crlf.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(crlf)) {
            out.write("第一行\r\n第二行\r\n第三行\r\n".getBytes(StandardCharsets.UTF_8));
        }
        BookPager crlfPager = new BookPager(new BookSource(crlf.getAbsolutePath()));
        crlfPager.setLinesPerPage(2);
        check("CRLF countLines", crlfPager.countLines(), 3);
        check("CRLF 首行不含 \\r", firstLine(crlfPager.jumpTo(0)).indexOf('\r') < 0, true);
        check("CRLF 第二行内容", firstLine(crlfPager.jumpTo(1)), "第二行");

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURES");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static String firstLine(String page) {
        String[] lines = page.split("\n");
        return lines.length > 0 ? lines[0] : "";
    }

    /**
     * 复刻 MainUi.displayPage()，用来看越界页码会显示成什么
     **/
    private static int displayPage(int cur, int lineCount) {
        return cur % lineCount == 0 ? cur / lineCount : cur / lineCount + 1;
    }

    private static void check(String name, Object actual, Object expected) {
        boolean ok = actual == null ? expected == null : actual.equals(expected);
        if (ok) {
            System.out.println("  ok   " + name + " = " + actual);
        } else {
            failures++;
            System.out.println("  FAIL " + name + ": expected " + expected + ", got " + actual);
        }
    }
}
