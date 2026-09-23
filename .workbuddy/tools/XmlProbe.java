import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XmlSerializer 行为探针：PersistentState 想从手写 getState/loadState 换成平台序列化，
 * 改造前先离线验证这些关键行为（2023.3 平台 jar）：
 * <ol>
 *   <li>serialize 的根元素名是否等于类简单名（现格式根是 PersistentState）；</li>
 *   <li>私有字段 + @Attribute + 同名 bean getter/setter：写出属性用的是字段值还是 getter 值；</li>
 *   <li>存储字段改名 + @Transient 标在 boolean is-getter 上（shellBlockEnabled 的规避方案）；</li>
 *   <li>没有 @Attribute 的字段是否会被写成子元素（会污染现有 XML 格式）；</li>
 *   <li>@Transient 标在字段上 / 标在无字段的方法上能否跳过；</li>
 *   <li>deserializeInto 是否走 setter、缺属性时字段是否保持 null（getter 兜底默认值是否可用）。</li>
 * </ol>
 * 第一轮结论：无 @Attribute 的字段会写成 option 子元素；@Attribute 字段与同名 is-getter 冲突时
 * 序列化被静默丢弃（反序列化却能读入）——不对称，存储字段必须改名。
 **/
public class XmlProbe {

    public static class Probe {
        @Attribute("fontSize")
        private String fontSize;
        @Attribute("bookPath")
        private String bookPath;
        @Attribute("shellBlockEnabled")
        private String shellBlockFlag;
        private String noAttr;
        @Transient
        private final Map<String, String> bookMap = new LinkedHashMap<>();

        public String getFontSize() {
            return fontSize == null || fontSize.isEmpty() ? "14" : fontSize;
        }

        public void setFontSize(String fontSize) {
            this.fontSize = fontSize;
        }

        public String getBookPath() {
            return bookPath == null ? "" : bookPath;
        }

        public void setBookPath(String bookPath) {
            this.bookPath = bookPath;
        }

        /** 存储字段改名 shellBlockFlag 避免与该 boolean 属性撞名；@Transient 防止被写成 option 元素 */
        @Transient
        public boolean isShellBlockEnabled() {
            return !"0".equals(shellBlockFlag);
        }

        public void setShellBlockEnabled(boolean enabled) {
            this.shellBlockFlag = enabled ? "1" : "0";
        }

        public String getNoAttr() {
            return noAttr;
        }

        public void setNoAttr(String noAttr) {
            this.noAttr = noAttr;
        }

        @Transient
        public List<String> getBookPathList() {
            return new ArrayList<>(bookMap.keySet());
        }
    }

    public static void main(String[] args) throws Exception {
        Probe p = new Probe();
        p.setFontSize("18");
        p.setBookPath("d:/book.txt");
        p.setNoAttr("should-not-matter");
        p.setShellBlockEnabled(false);
        Element e = XmlSerializer.serialize(p);
        System.out.println("[serialize] " + new XMLOutputter().outputString(e));

        // 字段为 null（只有 getter 兜底默认值）时写出什么
        Probe n = new Probe();
        System.out.println("[serialize-null] " + new XMLOutputter().outputString(XmlSerializer.serialize(n)));

        Element old = new Element("Probe");
        old.setAttribute("fontSize", "20");
        old.setAttribute("bookPath", "e:/old.txt");
        old.setAttribute("shellBlockEnabled", "0");
        Probe q = new Probe();
        XmlSerializer.deserializeInto(q, old);
        System.out.println("[deserialize] fontSize=" + q.getFontSize()
                + " (raw=" + q.fontSize + ")"
                + " bookPath=" + q.getBookPath()
                + " shellEnabled=" + q.isShellBlockEnabled()
                + " (raw=" + q.shellBlockFlag + ")"
                + " noAttr=" + q.getNoAttr());

        Element partial = new Element("Probe");
        Probe r = new Probe();
        r.setFontSize("9");
        XmlSerializer.deserializeInto(r, partial);
        System.out.println("[partial-load] fontSize=" + r.getFontSize() + " (raw=" + r.fontSize + ")");
    }
}
