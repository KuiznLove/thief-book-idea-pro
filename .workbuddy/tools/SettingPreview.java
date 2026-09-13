import com.thief.idea.ui.SettingUi;
import com.jgoodies.forms.layout.FormLayout;

import javax.swing.*;
import java.awt.*;

/**
 * 设置页离线自查：实例化 SettingUi，把 Style 面板里每个控件落在哪个 FormLayout 单元格打出来。
 * <p>
 * 用来验证"运行时 appendRow 追加的控件"（TTS 热键、代码语言下拉）没有和既有控件抢同一格。
 * <p>
 * 运行（classpath 同 UiPreview）：
 * java -cp "<platform>/lib/*;build/classes/java/main" .workbuddy/tools/SettingPreview.java
 **/
public class SettingPreview {

    public static void main(String[] args) {
        try {
            com.intellij.ui.scale.JBUIScale.setSystemScaleFactor(1f);
            com.intellij.ui.scale.JBUIScale.setUserScaleFactorForTest(1f);
        } catch (Throwable ignored) {
        }

        SettingUi ui = new SettingUi();
        dump("Style", ui.fontsPanel);
        dump("Hotkeys", ui.hotkeysPanel);
        dump("Reader", ui.readerPanel);
        System.exit(0);
    }

    private static void dump(String title, JPanel panel) {
        FormLayout layout = (FormLayout) panel.getLayout();
        System.out.println("[" + title + "] rows=" + layout.getRowCount() + " cols=" + layout.getColumnCount());
        for (Component component : panel.getComponents()) {
            String detail = "";
            if (component instanceof JLabel) {
                detail = ((JLabel) component).getText();
            } else if (component instanceof JComboBox) {
                detail = "selected=" + ((JComboBox<?>) component).getSelectedItem();
            } else if (component instanceof JTextField) {
                detail = "text=" + ((JTextField) component).getText();
            }
            // getConstraints 返回的就是 add 时用的那一格，能直接看出有没有重叠
            System.out.println("  " + layout.getConstraints(component) + "  "
                    + component.getClass().getSimpleName() + "  " + detail);
        }
    }
}
