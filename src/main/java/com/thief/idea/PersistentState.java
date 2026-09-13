package com.thief.idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.thief.idea.disguise.DisguiseContent;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@State(
        name = "PersistentState",
        storages = {@Storage(
                value = "thief-book.xml"
        )}
)
public class PersistentState implements PersistentStateComponent<Element> {

    /**
     * 阅读区字体为"系统默认"时跟随 IDE 默认字体（UIUtil.getLabelFont）
     **/
    public static final String DEFAULT_FONT = "系统默认";

    private String bookPathText;

    private String showFlag;

    private String fontSize;

    private String fontType;

    private String before;

    private String next;

    private String currentLine;

    private String lineCount;

    private String lineSpace;

    private String bossKey;

    /**
     * 朗读播放/停止热键，默认 Ctrl+4
     **/
    private String ttsKey;

    /**
     * 朗读语音显示名，空串表示系统默认
     **/
    private String ttsVoice;

    /**
     * 朗读语速倍率（字符串形式，如 "1.0"）
     **/
    private String ttsRate;

    /**
     * 全部书本：路径 -> 各自阅读进度（行号），顺序即设置页列表顺序。
     * 支持选择多本书并在阅读界面切换，每本书独立保存进度。
     **/
    private LinkedHashMap<String, String> bookMap = new LinkedHashMap<>();

    /**
     * 伪装用的"助手名称"（显示在阅读窗口顶部与 Tab 标题，可在窗口内右键修改）
     **/
    private String assistantName;

    /**
     * 伪装用的"模型名"（显示在输入框右下角）
     **/
    private String assistantModel;

    /**
     * 伪装用的"自定义模型名"（设置页 Style 面板可填，纯装饰，不需要 url / api key）：
     * 填了之后会作为一项出现在输入框右下角的模型列表里，见 ChatInputBar#setCustomModel
     **/
    private String customModel;

    /**
     * 左侧"历史记录"（epub 目录）是否被收起："1" 已收起，"0" 展开
     **/
    private String tocCollapsed;

    /**
     * 伪装代码卡片使用的语言（Python / Java / Vue），取值见 DisguiseContent.LANGUAGES
     **/
    private String codeLanguage;

    /**
     * 是否在自然段中间插入单行 shell 命令："0" 关闭，其它值（含未设置）都视为开启。
     * 用"非 0 即开"是为了让老配置升级上来时默认就带这个效果
     **/
    private String shellBlockEnabled;

    /**
     * 无图模式：是否隐藏电子书正文里的插图（设置页开关）。
     * <p>
     * 注意与 {@link #shellBlockEnabled} 的"非 0 即开"相反——这里是 **等于 "1" 才隐藏**，
     * 未设置 / 老配置一律视为 false（照常显示插图）。默认"显示"才不会让升级后的用户
     * 打开书发现插图凭空没了
     **/
    private String hideImages;

    public PersistentState() {
    }

    public static PersistentState getInstance() {
        return ApplicationManager.getApplication().getService(PersistentState.class);
    }


    @Nullable
    @Override
    public Element getState() {
        Element element = new Element("PersistentState");
        element.setAttribute("bookPath", this.getBookPathText());
        element.setAttribute("showFlag", this.getShowFlag());
        element.setAttribute("fontSize", this.getFontSize());
        element.setAttribute("before", this.getBefore());
        element.setAttribute("next", this.getNext());
        element.setAttribute("currentLine", this.getCurrentLine());
        element.setAttribute("fontType", this.getFontType());
        element.setAttribute("lineCount",this.getLineCount());
        element.setAttribute("lineSpace",this.getLineSpace());
        element.setAttribute("bossKey",this.getBossKey());
        element.setAttribute("ttsKey", this.getTtsKey());
        element.setAttribute("ttsVoice", this.getTtsVoice());
        element.setAttribute("ttsRate", this.getTtsRate());
        element.setAttribute("assistantName", this.getAssistantName());
        element.setAttribute("assistantModel", this.getAssistantModel());
        element.setAttribute("customModel", this.getCustomModel());
        element.setAttribute("tocCollapsed", this.getTocCollapsed());
        element.setAttribute("codeLanguage", this.getCodeLanguage());
        element.setAttribute("shellBlockEnabled", this.isShellBlockEnabled() ? "1" : "0");
        element.setAttribute("hideImages", this.isHideImages() ? "1" : "0");
        for (Map.Entry<String, String> entry : bookMap.entrySet()) {
            Element book = new Element("book");
            book.setAttribute("path", entry.getKey());
            book.setAttribute("line", entry.getValue());
            element.addContent(book);
        }

        return element;
    }

    @Override
    public void loadState(@NotNull Element state) {
        bookMap.clear();
        this.setBookPathText(state.getAttributeValue("bookPath"));
        this.setShowFlag(state.getAttributeValue("showFlag"));
        this.setFontSize(state.getAttributeValue("fontSize"));
        this.setBefore(state.getAttributeValue("before"));
        this.setNext(state.getAttributeValue("next"));
        this.setCurrentLine(state.getAttributeValue("currentLine"));
        this.setFontType(state.getAttributeValue("fontType"));
        this.setLineCount(state.getAttributeValue("lineCount"));
        this.setLineSpace(state.getAttributeValue("lineSpace"));
        this.setBossKey(state.getAttributeValue("bossKey"));
        this.setTtsKey(state.getAttributeValue("ttsKey"));
        this.setTtsVoice(state.getAttributeValue("ttsVoice"));
        this.setTtsRate(state.getAttributeValue("ttsRate"));
        this.setAssistantName(state.getAttributeValue("assistantName"));
        this.setAssistantModel(state.getAttributeValue("assistantModel"));
        this.setCustomModel(state.getAttributeValue("customModel"));
        this.setTocCollapsed(state.getAttributeValue("tocCollapsed"));
        this.setCodeLanguage(state.getAttributeValue("codeLanguage"));
        // 老配置没有这个属性（null）→ isShellBlockEnabled() 返回 true，保持"默认开启"
        this.shellBlockEnabled = state.getAttributeValue("shellBlockEnabled");
        // 老配置没有这个属性（null）→ isHideImages() 返回 false，保持"默认显示插图"
        this.hideImages = state.getAttributeValue("hideImages");
        for (Element book : state.getChildren("book")) {
            String path = book.getAttributeValue("path");
            if (path == null || path.isEmpty()) {
                continue;
            }
            bookMap.put(path, book.getAttributeValue("line"));
        }
        // 兼容旧版配置：只有 bookPath 属性、没有 book 子元素时，导入为第一本书
        String legacy = this.getBookPathText();
        if (!legacy.isEmpty() && !bookMap.containsKey(legacy)) {
            bookMap.put(legacy, this.currentLine);
        }

    }

    @Override
    public void noStateLoaded() {

    }

    public String getBookPathText() {
        return (bookPathText == null || bookPathText.isEmpty()) ? "" : this.bookPathText;
    }

    public void setBookPathText(String bookPathText) {
        this.bookPathText = bookPathText;
        if (bookPathText != null && !bookPathText.isEmpty() && !bookMap.containsKey(bookPathText)) {
            bookMap.put(bookPathText, this.currentLine != null ? this.currentLine : "0");
        }
    }

    /**
     * 全部书本路径（按添加顺序）
     **/
    public List<String> getBookPathList() {
        return new ArrayList<>(bookMap.keySet());
    }

    /**
     * 设置全部书本：保留已存在的进度，活动书被移除时自动切到列表第一本
     **/
    public void setBookPathList(List<String> paths) {
        LinkedHashMap<String, String> newMap = new LinkedHashMap<>();
        for (String path : paths) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            String progress = bookMap.get(path);
            newMap.put(path, progress != null ? progress : "0");
        }
        bookMap = newMap;
        if (!bookMap.containsKey(getBookPathText())) {
            String first = bookMap.isEmpty() ? "" : bookMap.keySet().iterator().next();
            setBookPathText(first);
        }
    }

    /**
     * 获取某本书的阅读进度（行号）；活动书回退到历史 currentLine，未读过的书返回 0
     **/
    public String getCurrentLineFor(String bookPath) {
        String line = bookMap.get(bookPath);
        if (line != null) {
            return line;
        }
        if (Objects.equals(bookPath, getBookPathText())) {
            return getCurrentLine();
        }
        return "0";
    }

    /**
     * 保存某本书的阅读进度（行号）；活动书同步更新历史 currentLine 属性
     **/
    public void setCurrentLineFor(String bookPath, String line) {
        if (bookPath == null || bookPath.isEmpty()) {
            return;
        }
        bookMap.put(bookPath, line);
        if (Objects.equals(bookPath, getBookPathText())) {
            this.currentLine = line;
        }
    }

    public String getShowFlag() {
        return (showFlag == null || showFlag.isEmpty()) ? "0" : this.showFlag;
    }

    public void setShowFlag(String showFlag) {
        this.showFlag = showFlag;
    }

    public String getBefore() {
        return (before == null || before.isEmpty()) ? "Ctrl+1" : this.before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    public String getNext() {
        return (next == null || next.isEmpty()) ? "Ctrl+2" : this.next;
    }

    public void setNext(String next) {
        this.next = next;
    }

    public String getCurrentLine() {
        return (currentLine == null || currentLine.isEmpty()) ? "0" : this.currentLine;
    }

    public void setCurrentLine(String currentLine) {
        this.currentLine = currentLine;
    }

    public String getFontSize() {
        return (fontSize == null || fontSize.isEmpty()) ? "14" : this.fontSize;
    }

    public void setFontSize(String fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontType() {
        return (fontType == null || fontType.isEmpty()) ? DEFAULT_FONT : this.fontType;
    }

    public void setFontType(String fontType) {
        this.fontType = fontType;
    }
    /**
     * 每页行数：默认 8 行，配合"AI 回复"排版看起来才像一段完整的回答
     * （旧版默认 1 行，未配置过的用户会直接拿到 8；已配置过的保持原值）
     **/
    public String getLineCount() {
        return this.lineCount = (lineCount == null || lineCount.isEmpty()) ? "8" : lineCount;
    }
    public void setLineCount(String lineCount) {
        this.lineCount = lineCount;
    }

    public String getLineSpace() {
        return this.lineSpace=(lineSpace == null || lineSpace.isEmpty()) ? "0" : lineSpace;
    }

    public void setLineSpace(String lineSpace) {
        this.lineSpace = lineSpace;
    }

    public String getBossKey() {
        return (bossKey == null || bossKey.isEmpty()) ? "Ctrl+3" : this.bossKey;
    }

    public void setBossKey(String bossKey) {
        this.bossKey = bossKey;
    }

    public String getTtsKey() {
        return (ttsKey == null || ttsKey.isEmpty()) ? "Ctrl+4" : this.ttsKey;
    }

    public void setTtsKey(String ttsKey) {
        this.ttsKey = ttsKey;
    }

    public String getTtsVoice() {
        return (ttsVoice == null) ? "" : this.ttsVoice;
    }

    public void setTtsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public String getTtsRate() {
        return (ttsRate == null || ttsRate.isEmpty()) ? "1.0" : this.ttsRate;
    }

    public void setTtsRate(String ttsRate) {
        this.ttsRate = ttsRate;
    }

    /**
     * 伪装用的助手名称，窗口内右键品牌名可修改
     **/
    public String getAssistantName() {
        return (assistantName == null || assistantName.isEmpty()) ? "CodePilot" : this.assistantName;
    }

    public void setAssistantName(String assistantName) {
        this.assistantName = assistantName;
    }

    /**
     * 伪装用的模型名，显示在输入框右下角的模型选择处
     **/
    public String getAssistantModel() {
        return (assistantModel == null || assistantModel.isEmpty()) ? "Seed-Code" : this.assistantModel;
    }

    public void setAssistantModel(String assistantModel) {
        this.assistantModel = assistantModel;
    }

    /**
     * 伪装用的"自定义模型名"（设置页可填，纯装饰）：只在界面上出现，不做任何网络调用。
     * 未设置时返回空串（而不是回落成某个默认模型名），便于调用方判断"有没有自定义模型"
     **/
    public String getCustomModel() {
        return customModel == null ? "" : customModel.trim();
    }

    public void setCustomModel(String customModel) {
        this.customModel = customModel;
    }

    /**
     * 左侧"历史记录"是否收起，默认展开
     **/
    public String getTocCollapsed() {
        return (tocCollapsed == null || tocCollapsed.isEmpty()) ? "0" : this.tocCollapsed;
    }

    public void setTocCollapsed(String tocCollapsed) {
        this.tocCollapsed = tocCollapsed;
    }

    /**
     * 伪装代码卡片使用的语言，默认 Python（与改造前的素材保持一致）
     **/
    public String getCodeLanguage() {
        return (codeLanguage == null || codeLanguage.isEmpty())
                ? DisguiseContent.DEFAULT_LANGUAGE : this.codeLanguage;
    }

    public void setCodeLanguage(String codeLanguage) {
        this.codeLanguage = codeLanguage;
    }

    /**
     * 是否在自然段中间插单行 shell 命令（设置页 "Shell snippet" 开关）。
     * 未设置 / 老配置一律视为开启
     **/
    public boolean isShellBlockEnabled() {
        return !"0".equals(shellBlockEnabled);
    }

    public void setShellBlockEnabled(boolean enabled) {
        this.shellBlockEnabled = enabled ? "1" : "0";
    }

    /**
     * 无图模式（设置页 "Hide book images"）：隐藏正文里的电子书插图，默认关闭。
     * 与 {@link #isShellBlockEnabled()} 的默认值方向相反，原因见字段注释
     **/
    public boolean isHideImages() {
        return "1".equals(hideImages);
    }

    public void setHideImages(boolean hide) {
        this.hideImages = hide ? "1" : "0";
    }
}