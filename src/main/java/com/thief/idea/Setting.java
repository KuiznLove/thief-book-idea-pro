package com.thief.idea;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.thief.idea.ui.SettingUi;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class Setting implements SearchableConfigurable {


    private SettingUi settingUi;

    private PersistentState persistentState = PersistentState.getInstance();


    @SuppressWarnings("FieldCanBeLocal")
    private final Project project;


    public Setting(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    @Override
    public String getId() {
        return "thief.id";
    }

    @Nullable
    @Override
    public Runnable enableSearch(String option) {
        return null;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "AI Assistant Config";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {

        if (settingUi == null) {
            settingUi = new SettingUi();
        }
        settingUi.innit(persistentState);

        return settingUi.mainPanel;

    }

    @Override
    public boolean isModified() {
        return !Objects.equals(persistentState.getBookPathList(), settingUi.getBookList())
                || !Objects.equals(persistentState.getFontSize(), settingUi.fontSize.getSelectedItem().toString())
                || !Objects.equals(persistentState.getBefore(), settingUi.before.getText())
                || !Objects.equals(persistentState.getNext(), settingUi.next.getText())
                || !Objects.equals(persistentState.getLineCount(), settingUi.lineCount.getSelectedItem().toString())
                || !Objects.equals(persistentState.getLineSpace(), settingUi.lineSpace.getSelectedItem().toString())
                || !Objects.equals(persistentState.getFontType(), settingUi.getSelectedFontType())
                || !Objects.equals(persistentState.getBossKey(), settingUi.bossKey.getText())
                || !Objects.equals(persistentState.getTtsKey(), settingUi.ttsKey.getText())
                || !Objects.equals(persistentState.getCodeLanguage(), settingUi.getSelectedCodeLanguage())
                || !Objects.equals(persistentState.getCustomModel(), settingUi.getCustomModel())
                || persistentState.isShellBlockEnabled() != settingUi.shellBlock.isSelected()
                || persistentState.isHideImages() != settingUi.hideImages.isSelected();

    }

    @Override
    public void apply() {
        persistentState.setBookPathList(settingUi.getBookList());
        persistentState.setFontSize(settingUi.fontSize.getSelectedItem().toString());
        persistentState.setBefore(settingUi.before.getText());
        persistentState.setNext(settingUi.next.getText());
        persistentState.setLineCount(settingUi.lineCount.getSelectedItem().toString());
        persistentState.setFontType(settingUi.getSelectedFontType());
        persistentState.setLineSpace(settingUi.lineSpace.getSelectedItem().toString());
        persistentState.setBossKey(settingUi.bossKey.getText());
        persistentState.setTtsKey(settingUi.ttsKey.getText());
        persistentState.setCodeLanguage(settingUi.getSelectedCodeLanguage());

        // 自定义模型（纯伪装）：只在名字真的改了的时候才动当前模型，
        // 否则用户"只改了字号"点一次 Apply 也会把他在输入框里选的模型顶掉
        String customModel = settingUi.getCustomModel();
        if (!Objects.equals(persistentState.getCustomModel(), customModel)) {
            persistentState.setCustomModel(customModel);
            if (!customModel.isEmpty()) {
                persistentState.setAssistantModel(customModel);
            }
        }

        persistentState.setShellBlockEnabled(settingUi.shellBlock.isSelected());
        persistentState.setHideImages(settingUi.hideImages.isSelected());

        // 让已打开的工具窗口立即应用新设置，无需再手动点刷新
        MainUi mainUi = MainUi.getInstance(project);
        if (mainUi != null) {
            mainUi.refresh();
        }

        // 点击 Apply 时同样立即把应用级设置落盘（thief-book.xml），与点击 OK 行为一致；
        // 否则平台只在 OK 时强制保存，Apply 只改内存，IDE 异常退出后设置会丢失
        SaveAndSyncHandler.getInstance().scheduleSave(new SaveAndSyncHandler.SaveTask(null, true));
    }

    @Override
    public void reset() {
//        settingUi.showFlag.setSelected(false);
//        settingUi.fontSize.setSelectedItem("5");
//        settingUi.before.setText("");
//        settingUi.next.setText("");
    }

    @Override
    public void disposeUIResources() {

    }
}