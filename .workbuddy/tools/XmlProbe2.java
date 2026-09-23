import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

public class XmlProbe2 {
    public static class LikeService {
        @Attribute("fontSize") private String fontSize;
        // 模拟 PersistentStateComponent<Element>::getState —— 无字段、返回 Element 的 getter
        public Element getState() { Element e = new Element("dummy"); e.setAttribute("x", "1"); return e; }
        public String getFontSize() { return fontSize; }
        public void setFontSize(String s) { this.fontSize = s; }
    }
    public static class LikeServiceTransient {
        @Attribute("fontSize") private String fontSize;
        @Transient public Element getState() { Element e = new Element("dummy"); e.setAttribute("x", "1"); return e; }
        public String getFontSize() { return fontSize; }
        public void setFontSize(String s) { this.fontSize = s; }
    }
    public static void main(String[] args) throws Exception {
        LikeService a = new LikeService();
        a.setFontSize("12");
        try {
            System.out.println("[no-transient] " + new XMLOutputter().outputString(XmlSerializer.serialize(a)));
        } catch (Throwable t) {
            System.out.println("[no-transient] FAILED: " + t);
        }
        LikeServiceTransient b = new LikeServiceTransient();
        b.setFontSize("12");
        try {
            System.out.println("[transient]    " + new XMLOutputter().outputString(XmlSerializer.serialize(b)));
        } catch (Throwable t) {
            System.out.println("[transient] FAILED: " + t);
        }
    }
}
