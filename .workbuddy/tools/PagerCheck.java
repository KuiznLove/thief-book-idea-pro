import com.thief.idea.book.BookPager;
import com.thief.idea.book.BookSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * BookPager 语义自测：分页引擎从 MainUi 抽出后，用它验证翻页/跳页/进度语义与旧实现一致。
 * <p>
 * 运行（需要平台 lib + epublib + jsoup 在 classpath，另加 build/classes/java/main）：
 * java -Dfile.encoding=UTF-8 -cp "<platform>/lib/*;<epublib jar>;<jsoup jar>;build/classes/java/main" PagerCheck.java
 * <p>
 * 全部用例通过时打印 "ALL PASS"，否则打印第一处失败。
 **/
public class PagerCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        // —— 样本 1：1005 行短行（文件约 10KB，会跨 8KB 块边界）——
        File txt = File.createTempFile("pagercheck-", ".txt");
        txt.deleteOnExit();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 1005; i++) {
            sb.append(String.format("line-%04d", i)).append('\n');
        }
        write(txt, sb.toString(), StandardCharsets.UTF_8);

        BookSource source = new BookSource(txt.getAbsolutePath());
        BookPager pager = new BookPager(source);
        pager.setLinesPerPage(20);
        pager.setLineSpacing(0);

        check("countLines", pager.countLines(), 1005);
        pager.setCurrentPage(0);

        // 首次加载（progress=0）：读第 1 页，currentPage 推进到 20
        String page1 = pager.reloadCurrent();
        check("reloadCurrent p1 first", firstLine(page1), "line-0001");
        check("reloadCurrent p1 last", lastLine(page1), "line-0020");
        check("reloadCurrent p1 currentPage", pager.currentPage(), 20);

        // 下一页
        String page2 = pager.turnNext();
        check("turnNext p2 first", firstLine(page2), "line-0021");
        check("turnNext p2 currentPage", pager.currentPage(), 40);

        // 上一页：40%20==0 → 回退两页再读回一页 → 第 1 页
        String back = pager.turnBack();
        check("turnBack first", firstLine(back), "line-0001");
        check("turnBack currentPage", pager.currentPage(), 20);

        // 跳页（等价旧 jumpToPage 的 countSeek+readBook）
        String p13 = pager.jumpTo(240);
        check("jumpTo p13 first", firstLine(p13), "line-0241");
        check("jumpTo p13 last", lastLine(p13), "line-0260");
        check("jumpTo p13 currentPage", pager.currentPage(), 260);

        // 设置页 Apply 后的重读：进度不变，内容不变
        String reloaded = pager.reloadCurrent();
        check("reloadCurrent keep progress", pager.currentPage(), 260);
        check("reloadCurrent same content", reloaded.replace("\n", "|"), p13.replace("\n", "|"));

        // 最后一页（不足一页）与书末再翻（得到空页且页码不再推进）。
        // jumpTo(1000) 语义 = 前 1000 行已读，从 line-1001 读到文件尾
        String tail = pager.jumpTo(1000);
        check("tail first", firstLine(tail), "line-1001");
        check("tail lines", countLines(tail), 5);
        check("tail currentPage", pager.currentPage(), 1005);
        String beyond = pager.turnNext();
        check("beyond empty", beyond.isEmpty(), true);
        check("beyond currentPage unchanged", pager.currentPage(), 1005);

        // 行间距：每行之间插 2 个空行
        pager.setLineSpacing(2);
        String spaced = pager.jumpTo(0);
        check("spacing non-empty lines", countLines(spaced), 20);
        check("spacing total segments", spaced.split("\n", -1).length, 20 * 3 + 1);
        pager.setLineSpacing(0);

        // —— 样本 2：长中文行（UTF-8 每行 ~180B，300 行 ~54KB，反复跨块边界）——
        StringBuilder cn = new StringBuilder();
        for (int i = 1; i <= 300; i++) {
            for (int k = 0; k < 30; k++) {
                cn.append("天地玄黄宇宙洪荒");
            }
            cn.append('#').append(i).append('\n');
        }
        File cnFile = File.createTempFile("pagercheck-cn-", ".txt");
        cnFile.deleteOnExit();
        write(cnFile, cn.toString(), StandardCharsets.UTF_8);
        BookPager cnPager = new BookPager(new BookSource(cnFile.getAbsolutePath()));
        cnPager.setLinesPerPage(7);
        check("cn countLines", cnPager.countLines(), 300);
        String cnPage = cnPager.jumpTo(42);
        check("cn jumpTo line tail", lastLine(cnPage).endsWith("#49"), true);
        check("cn jumpTo full rows", countLines(cnPage), 7);

        // —— 样本 3：GB18030 与 UTF-8 BOM ——
        File gb = File.createTempFile("pagercheck-gb-", ".txt");
        gb.deleteOnExit();
        write(gb, "寒来暑往，秋收冬藏。\n闰余成岁，律吕调阳。\n", gb18030());
        BookPager gbPager = new BookPager(new BookSource(gb.getAbsolutePath()));
        gbPager.setLinesPerPage(1);
        String gbPage = gbPager.jumpTo(0);
        check("gb18030 decode", firstLine(gbPage), "寒来暑往，秋收冬藏。");

        File bom = File.createTempFile("pagercheck-bom-", ".txt");
        bom.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(bom)) {
            out.write(0xEF);
            out.write(0xBB);
            out.write(0x0BF);
            out.write("金生丽水，玉出昆冈。\n".getBytes(StandardCharsets.UTF_8));
        }
        BookPager bomPager = new BookPager(new BookSource(bom.getAbsolutePath()));
        bomPager.setLinesPerPage(2);
        String bomPage = bomPager.jumpTo(0);
        check("utf8 bom stripped", firstLine(bomPage), "金生丽水，玉出昆冈。");

        if (failures == 0) {
            System.out.println("ALL PASS");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    private static void write(File file, String text, java.nio.charset.Charset charset) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
            writer.write(text);
        }
    }

    private static java.nio.charset.Charset gb18030() {
        return java.nio.charset.Charset.forName("GB18030");
    }

    private static String firstLine(String page) {
        return page.split("\n")[0];
    }

    private static String lastLine(String page) {
        List<String> lines = java.util.Arrays.stream(page.split("\n"))
                .filter(l -> !l.isEmpty()).collect(java.util.stream.Collectors.toList());
        return lines.get(lines.size() - 1);
    }

    private static int countLines(String page) {
        return (int) java.util.Arrays.stream(page.split("\n"))
                .filter(l -> !l.isEmpty()).count();
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
