import com.thief.idea.PersistentState;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

/**
 * PersistentState 格式兼容自测：序列化从手写 getState/loadState 换成平台 XmlSerializer 后，
 * 验证 thief-book.xml 的读写格式与旧版逐属性兼容。
 * <ul>
 *   <li>旧格式 Element（含全部属性 + book 子元素）→ loadState → 各 getter 取值正确；</li>
 *   <li>loadState 后 getState() 写出的根名 / 属性 / book 子元素与旧格式一致；</li>
 *   <li>只有 bookPath 属性、没有 book 子元素的老配置能导入为第一本书；</li>
 *   <li>未设置的字段保存时跳过、读回时走 getter 默认值。</li>
 * </ul>
 * 运行：java -Dfile.encoding=UTF-8 -cp "<platform>/lib/*;build/classes/java/main" StateCheck.java
 **/
public class StateCheck {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        // —— 旧版 getState() 会写出的完整格式 ——
        Element old = new Element("PersistentState");
        old.setAttribute("bookPath", "D:/books/sanguo.txt");
        old.setAttribute("showFlag", "0");
        old.setAttribute("fontSize", "16");
        old.setAttribute("before", "Ctrl+1");
        old.setAttribute("next", "Ctrl+2");
        old.setAttribute("currentLine", "260");
        old.setAttribute("fontType", "系统默认");
        old.setAttribute("lineCount", "20");
        old.setAttribute("lineSpace", "2");
        old.setAttribute("bossKey", "Ctrl+3");
        old.setAttribute("ttsKey", "Ctrl+4");
        old.setAttribute("ttsVoice", "Microsoft Huihui");
        old.setAttribute("ttsRate", "1.25");
        old.setAttribute("assistantName", "CodePilot");
        old.setAttribute("assistantModel", "Seed-Code");
        old.setAttribute("customModel", "");
        old.setAttribute("tocCollapsed", "0");
        old.setAttribute("codeLanguage", "Java");
        old.setAttribute("shellBlockEnabled", "0");
        old.setAttribute("hideImages", "1");
        addBook(old, "D:/books/sanguo.txt", "260");
        addBook(old, "D:/books/epubs/凡人修仙传.epub", "5120");

        PersistentState state = new PersistentState();
        state.loadState(old);
        check("fontSize", state.getFontSize(), "16");
        check("lineCount", state.getLineCount(), "20");
        check("lineSpace", state.getLineSpace(), "2");
        check("fontType", state.getFontType(), "系统默认");
        check("codeLanguage", state.getCodeLanguage(), "Java");
        check("ttsRate", state.getTtsRate(), "1.25");
        check("shell off", state.isShellBlockEnabled(), false);
        check("images hidden", state.isHideImages(), true);
        check("book list size", state.getBookPathList().size(), 2);
        check("book progress", state.getCurrentLineFor("D:/books/epubs/凡人修仙传.epub"), "5120");
        check("active book progress", state.getCurrentLineFor("D:/books/sanguo.txt"), "260");

        // —— 回写：根名、关键属性、book 子元素必须与旧格式一致 ——
        Element saved = state.getState();
        check("root name", saved.getName(), "PersistentState");
        check("attr bookPath", saved.getAttributeValue("bookPath"), "D:/books/sanguo.txt");
        check("attr fontSize", saved.getAttributeValue("fontSize"), "16");
        check("attr shellBlockEnabled", saved.getAttributeValue("shellBlockEnabled"), "0");
        check("attr hideImages", saved.getAttributeValue("hideImages"), "1");
        check("attr codeLanguage", saved.getAttributeValue("codeLanguage"), "Java");
        check("book children", saved.getChildren("book").size(), 2);
        Element firstBook = saved.getChildren("book").get(0);
        check("book path", firstBook.getAttributeValue("path"), "D:/books/sanguo.txt");
        check("book line", firstBook.getAttributeValue("line"), "260");
        System.out.println("[saved xml] " + new XMLOutputter().outputString(saved));

        // —— 修改后回写：开关与进度要落盘 ——
        state.setShellBlockEnabled(true);
        state.setCurrentLineFor("D:/books/sanguo.txt", "300");
        Element resaved = state.getState();
        check("attr shellBlockEnabled on", resaved.getAttributeValue("shellBlockEnabled"), "1");
        check("book line updated", resaved.getChildren("book").get(0).getAttributeValue("line"), "300");

        // —— 老配置：只有 bookPath 属性、没有 book 子元素 ——
        Element legacy = new Element("PersistentState");
        legacy.setAttribute("bookPath", "D:/books/old.txt");
        legacy.setAttribute("currentLine", "88");
        PersistentState legacyState = new PersistentState();
        legacyState.loadState(legacy);
        check("legacy book imported", legacyState.getBookPathList(), java.util.Collections.singletonList("D:/books/old.txt"));
        check("legacy progress", legacyState.getCurrentLineFor("D:/books/old.txt"), "88");

        // —— 全新状态：保存跳过未设置字段，读回走 getter 默认值 ——
        Element fresh = new PersistentState().getState();
        check("fresh no books", fresh.getChildren("book").size(), 0);
        PersistentState reloaded = new PersistentState();
        reloaded.loadState(fresh);
        check("fresh default fontSize", reloaded.getFontSize(), "14");
        check("fresh default lineCount", reloaded.getLineCount(), "8");
        check("fresh default shell on", reloaded.isShellBlockEnabled(), true);
        check("fresh default images shown", reloaded.isHideImages(), false);
        check("fresh default bossKey", reloaded.getBossKey(), "Ctrl+3");

        if (failures == 0) {
            System.out.println("ALL PASS");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    private static void addBook(Element parent, String path, String line) {
        Element book = new Element("book");
        book.setAttribute("path", path);
        book.setAttribute("line", line);
        parent.addContent(book);
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
