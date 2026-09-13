# AGENTS.md

IntelliJ IDEA 插件项目（thief-book-idea，IDE 内"摸鱼"小说阅读器）。Java 编写，面向 IntelliJ Platform。

## 构建方式（Gradle + IntelliJ Platform Gradle Plugin）
- 仓库使用 Gradle（`org.jetbrains.intellij` 插件）构建，不再使用 DevKit 模块方式。
- 构建入口：`./gradlew buildPlugin`，产物在 `build/distributions/thief-book-idea-<version>.zip`；单独打 jar 用 `./gradlew jar`。
- 本地起沙箱调试：`./gradlew runIde`（会自动下载 IntelliJ Platform 到 `~/.gradle` 缓存）。
- 修改后请运行 `./gradlew build` 验证编译与打包。

## 环境要求
- **JDK 17**：Gradle 守护进程使用 JDK 17（在 `gradle.properties` 的 `org.gradle.java.home` 中指定本机路径，路径不同请修改，当前为 `E:/Java/jdk-17`）。用 JDK 8/11 会因平台类字节码版本报错。
- Gradle Wrapper 版本 8.4（`gradle/wrapper/gradle-wrapper.properties`）。
- 构建用 IntelliJ Platform 版本在 `gradle.properties` 的 `intellijVersion`（默认 2023.3，社区版），最低兼容 IntelliJ Platform 2023.3（build 233）。
- **兼容版本由 `build.gradle` 的 `patchPluginXml` 强制指定**（`sinceBuild = '233.0'`、`untilBuild = ''`），只改 `plugin.xml` 的 `idea-version` 无效，需两处同步。注意 `gradle.properties` 顶部注释里的 `since-build="203.0"` 是过时残留，勿信。
- 插件版本号有两处：`plugin.xml` 的 `<version>` 与 `build.gradle` 的 `version` 默认值，**后者会在构建时覆盖前者**（`patchPluginXml` 用 project.version 打补丁），两处都要改。
- **本机（这台 Windows 工作机）构建方式**：Git Bash 缺 coreutils，`./gradlew` 脚本跑不了（缺 `dirname`/`sed` 等），改用 wrapper jar 直接启动：
  `E:/Java/jdk-17/bin/java.exe -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain jar`
  需要下载依赖时补 `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dorg.gradle.jvmargs="-Xmx2048m -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"`（Clash 代理）。

## 源码布局（Gradle 标准目录）
- `src/main/java/` —— Java 源码根，包 `com.thief.idea`。
- `src/main/resources/` —— 资源根，含 `META-INF/plugin.xml` 与 `icons/`。

## 插件入口（真正的装配在 `src/main/resources/META-INF/plugin.xml`）
- 工具窗口 id 为 **`ai-assistant`**（`MainUi.TOOL_WINDOW_ID`），底部显示名 "CodePilot"（来自 `messages/AssistantBundle.properties` 的 `toolwindow.stripe.ai-assistant`）。改 id 必须同步 `ShowThiefBook`（它引用 `MainUi.TOOL_WINDOW_ID`）。
- `com.thief.idea.MainUi` —— `ToolWindowFactory`，AI 助手面板（底部）。
- `com.thief.idea.Setting` —— `SearchableConfigurable`，`Settings → Other Settings → AI Assistant Config`。
- `com.thief.idea.PersistentState` —— `applicationService` + `PersistentStateComponent<Element>`，持久化到 `thief-book.xml`。`getInstance()` 通过 `ApplicationManager.getApplication().getService(...)` 获取。
- `com.thief.idea.ShowThiefBook` —— 注册在 `WindowMenu` 的 action（text 为 "Show CodePilot"），用于重新打开被关闭的工具窗口。

## 界面伪装（AI 编码助手外观）
目标：窗口看起来是一个 AI 编码助手面板，小说正文就是"助手的回复"，并插入伪造的代码块。
- 素材与话术集中在 `com.thief.idea.disguise.DisguiseContent`：开场白、小标题、结尾话术、伪造代码片段（含文件名/语言角标）、行内代码正则所需的输入范围、命令回复、提示文案（TOAST_*）。要改"AI 味"就改这里，不要在 UI 代码里写死文案。
  - **代码片段按语言分三组**：`PYTHON_SNIPPETS`（图网络/训练）/ `JAVA_SNIPPETS`（Spring Boot 智能客服）/ `VUE_SNIPPETS`（Vue 3 + TS），每组 8 个（与 `AssistantPageView.MAX_EXTRA_CARDS + 1` 对齐）。`snippet(language, seed, index)` 用 `+ index` 取模，保证同页 index 不同必然取到不同片段。**加新语言（或给某组减量）时必须保证组内数量 ≥ 页内最大卡片数**，否则同一页会出现两张一模一样的卡片。语言常量、下拉项白名单在 `LANGUAGES`，未知值一律回退 Python。
  - 素材行长度保持在 64 字符内（等宽字体下不折行），这是"看起来像真实 diff"的关键；文件角标 4 个字母以内（`PY` / `YAML` / `JAVA` / `VUE` / `TS`）。
- 样式集中在 `com.thief.idea.ui.AssistantTheme`：**所有颜色都按"亮色/暗色"配对定义（JBColor）**，禁止在业务代码里写死颜色；字体走 `UIUtil.getFontWithFallback`（保证中文不出方框）。`GlyphButton`/`RoundedPanel` 也在这里。
- 矢量图标在 `com.thief.idea.ui.AssistantIcons`（手绘 Graphics2D，不依赖平台图标集与字体符号）。工具窗口图标 `icons/assistant.png` + `assistant@2x.png` 由 `.workbuddy/tools/IconGen.java` 生成（`java IconGen.java src/main/resources/icons`）。
- 阅读页渲染在 `com.thief.idea.ui.AssistantPageView`：`render(正文, seed, 字体, 段间距, 语言)` 输出 开场白 + 顶部 diff 卡片 + 段落之间穿插的卡片（带小标题）+ 结尾话术。正文里的英文/数字会被渲染成灰底行内代码块。**seed 只由 `bookFile + currentPage` 派生**（`MainUi.pageSeed()`），保证同一页刷新/老板键恢复时渲染结果一致，翻页才换一批。
  - **卡片分布按"累计字数"，不是按"第几段"**：`CHARS_PER_CARD = 250`（约等于一屏正文的字数）决定疏密，`MAX_EXTRA_CARDS = 7` 是上限——加上顶部那张正好用满 `DisguiseContent` 的 8 个片段，再多就会同页出现重复卡片。段落长短差异极大（长段能占满一屏、短段只有一行），按段落序号均分会造成"整屏都是正文、看不到代码块"（用户报过这个问题），改这两个常量前先估一下"一屏正文大约多少字"。
  - **单行 shell 块是另一套独立逻辑**（`shellSlots()` / `splitPoint()` / `shellBlock()` / `shellBody()`，素材在 `DisguiseContent.SHELL_SCRIPTS`，**不随代码语言切换**，由设置页开关 `PersistentState.shellBlockEnabled` 控制）：它不像 diff 卡片那样落在段落*之间*，而是把一个自然段从中间**切开**插进去。约束有四条，别拆：
    1. 只切 ≥ `MIN_SHELL_PARAGRAPH`（40 字）且**中间 35%~65% 区间里有句末标点**（`。！？；…`）的段落——找不到切点就整段不插，绝不在句子中间硬断；
    2. 一页最多 `MAX_SHELL_BLOCKS = 2` 条，在候选段落里按 stride 均匀取（落点由正文决定、不随机，保证老板键恢复时同页一致）；
    3. 含 epub 图片占位的段落直接跳过；
    4. **shell 块的头部与 diff 卡片完全同构**（`SH` 角标 + `scripts/xxx.sh` 文件名 + 复制/导出/刷新 + 主按钮），只有主按钮换成 "Run"（播放三角、`AssistantIcons.play()`），底色/描边也复用 `AssistantTheme.CARD_BG` / `CARD_BORDER`——**给 diff 卡片头部加元素时，shell 块要一起改**，否则一眼就能看出两类卡片不是同一个"助手"给的。
     阈值是标定过的：60 字切出来最好看，但实测一段 25 段的正文里只有个位数段落达标，功能等于白加；40 字是"还看得出两截、又足够常见"的下限。
  - 卡片只会落在段落之间，所以**单个超长段落内部插不进卡片**；整页只有一个落脚点时（段落极少），由结尾话术前的那张兜底卡片补一张。
  - 该面板直接作为滚动视图并实现 `Scrollable#getScrollableTracksViewportWidth=true`，段落是内部类 `ParagraphPane`（BoxLayout 下按已知宽度自算换行高度）。**改布局时不要退化成把 JTextPane 直接塞进 BoxLayout**，否则换行高度会错乱。
  - 代码卡片头部是 BorderLayout（左文件名 / 右工具栏），**宽度不够时两侧会直接重叠**（左侧"历史记录"展开 + 工具窗口被拖到 350px 时，卡片只剩 110px）。两级宽度预算都封在 `layoutCardHeader()` 里，diff 卡片与 shell 块的 `doLayout()` 都调它，**三级降级**：
    1. 先收窄文件名——`FileNameLabel.fitTo()` 丢掉目录前缀（`models/DSHGCN.py` → `DSHGCN.py`），仍放不下再尾部省略号；连 `available <= 0` 时清空文本（早期版本这里直接 `return`，结果是文件名原样留着跟工具栏叠字）。
    2. 还放不下就隐藏 `CardToolbar.minor`（复制/导出/刷新三个次要图标），只留主按钮 Apply / Run。
    3. 连主按钮都放不下（工具窗口拖到极窄）就整条工具栏 `setVisible(false)`，头部只剩角标 + 文件名。
    - **两个宽度必须在 `CardToolbar` 构造时算死缓存**（`fullWidth` / `primaryWidth`）：`FlowLayout.getPreferredSize()` 会跳过不可见组件，收起 minor 之后它就不再等于完整宽度；若拿它当判据，会出现"收起 → 宽度变小 → 判定放得下 → 又展开 → 叠字"的来回抖动（踩过）。
    - 往卡片头部加元素时，记得同步 `layoutCardHeader()` 的 `badge` 余量与 `CardToolbar` 的宽度预算。
  - 行宽/全宽依赖 `AssistantTheme.stretch()`（显式放开 maximumSize）。
- 底部输入框在 `com.thief.idea.ui.ChatInputBar`：外观是完全的 AI 助手输入区（占位提示、@ / # / 图片按钮、模型选择、绿色发送按钮），**实际用途是阅读导航**。交互映射（改这里要同步 README 与插件描述）：
  - 回车 / 点发送按钮 = 下一页；输入框内 ↑ = 上一页、↓ = 下一页；Esc 清空
  - 输入纯数字回车（或 `/page N`）= 跳到第 N 页
  - `/next` `/prev` `/play` `/stop` `/boss` `/help` = 对应操作
  - 其它任意输入 = 伪装回复（先"正在分析工作区上下文…"，再随机一句助手话术），不影响阅读
  - 提示/反馈一律走 `MainUi.toast()` → 输入框上方的提示行（自动消失），不要弹真的对话框
  - 右下角"模型选择"（`ChatInputBar.modelButton`）：列表 = `DisguiseContent.MODELS` 预设 + 设置页 `Custom model` 填的自定义名（`ChatInputBar.setCustomModel()`，去重后追加在末尾）。选中的名字存 `PersistentState.assistantModel`（默认 Seed-Code）。**纯装饰，不发任何请求**；自定义名可能很长，右侧那一行不要再往里塞控件。
- 顶部伪装栏（`MainUi.initHeaderBar`）：品牌名（`PersistentState.assistantName`，默认 CodePilot，窗口内右键品牌名可重命名）、**侧栏开关按钮**、"会话 N" 切换按钮（= 多本书切换，仅多书时显示，条目 tooltip 是真实路径）、页码指示（伪装成"上下文进度"，tooltip 为"上下文 x / y"）、上一条/下一条回复按钮（= 上一页/下一页）、喇叭按钮（= 离线朗读，**右键**弹 Voice / Rate 菜单，取代旧的下拉框）、5x5 隐形老板键按钮。
  - 侧栏开关（`MainUi.tocToggleButton` + `AssistantIcons.sidebar(size, color, expanded)`）伪装成 IDE 的"收起侧边栏"图标：**只有 epub 且有目录时才出现**，点击走 `toggleToc()` 收起/展开左侧"历史记录"，折叠状态存在 `PersistentState.tocCollapsed`（"1"/"0"，重启后保持）；图标按状态切换——展开态左列填实、收起态只留一条描边。收起后正文区变宽，由 `AssistantPageView` 的 `componentResized → applyContentWidth()` 自动按新宽度重算换行，不要在 `MainUi` 里手工改段落宽度。
  - 不要把这个开关挂到底部输入框的发送键上：发送键必须保持"下一页"语义，否则伪装会露馅。
- 左侧 epub 目录面板伪装成"历史记录"（可被上面的侧栏开关收起）。老板键（Ctrl+3）隐藏时：顶部栏/输入框/左侧列表全部收起，滚动区换成 `bossView()` 的假 Terminal 输出，Tab 标题改 "Terminal"、图标改 Console；恢复时换回 `pageView` 并 `renderCurrent()` 重绘当前页。
- 伪装残余（改动时留意，别把它们再暴露到界面上）：插件 id 仍是 `com.thief.idea`、vendor 仍指向原仓库、持久化文件名仍是 `thief-book.xml`、`static/*.png` 里的截图仍是旧界面。

## 界面预览（不启动 IDE 直接渲染截图）
伪装界面可以离线渲染成 PNG 校对排版，工具在 `.workbuddy/tools/`：
1. 平台目录在 gradle 缓存里：`~/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2023.3/<hash>/ideaIC-2023.3`（`<hash>` 是随机的，用通配或手动找；本机当前为 `6105b81c6142f62379ad6c5afb542c77350a71eb`）。
2. 先构建出 class 文件（`jar` 任务即可），再运行：
   `E:/Java/jdk-17/bin/java.exe -Dfile.encoding=UTF-8 --add-exports=java.desktop/sun.font=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.event=ALL-UNNAMED -cp "<平台目录>/lib/*;build/classes/java/main" .workbuddy/tools/UiPreview.java 输出.png 560 900`
   - 一次输出 8 张图：`输出.png`（侧栏展开）/ `输出-collapsed.png`（收起）/ `输出-long.png`（长页，核对卡片与 shell 块的分布，画布自动加高）/ `输出-java.png` 与 `输出-vue.png`（另两套素材的角标与代码风格）/ `输出-model.png`（故意用 38 字符的自定义模型名，核对输入框右下角不会被撑变形）/ `输出-noshell.png`（关掉 shell 开关，画布与 `-long` 一致，方便两张对照）/ `输出-narrow.png`（固定 400px + 侧栏展开，正文区只剩约 174px，专门核对卡片头部的三级降级不失效）。
   - 分布统计会同时打印 `card #n` 与 `shell #n` 的 y/百分比——单行 shell 块有没有插进去、插在哪，看这个比肉眼看缩略图靠谱。
   - `--add-exports=java.desktop/sun.font` 不能省，否则 `UIUtil.getFontWithFallback` 抛 `IllegalAccessError`。
   - JBUI 的缩放因子必须在任何 UI 类之前手工预置（`JBUIScale.setSystemScaleFactor(1f)`），否则报 `Must be precomputed`。
   - 只能渲染亮色：切 `DarculaLaf` 在没有 Application 的环境里会卡死，暗色配色靠 `AssistantTheme` 的 JBColor 配对保证。
3. 设置页自查：`.workbuddy/tools/SettingPreview.java` 实例化 `SettingUi`，打印每个控件占用的 FormLayout 单元格与当前值，用来验证"运行时追加的控件"（TTS 热键 / 代码语言下拉 / 自定义模型名）没有和既有控件抢格。**它比 UiPreview 需要更多 `--add-opens`**，至少补 `--add-opens=java.desktop/javax.swing=ALL-UNNAMED`，否则 `FontComboBox` 初始化时 `GraphicsUtil` 会抛 `InaccessibleObjectException`；同样别忘了 `-Dfile.encoding=UTF-8`（否则打印出来的中文标签是乱码）。
4. 图标生成：`E:/Java/jdk-17/bin/java.exe .workbuddy/tools/IconGen.java src/main/resources/icons`（输出 `assistant.png` + `assistant@2x.png`）。
5. shell 块阈值标定：`node .workbuddy/tools/shellslots.js` 复算两个预览样本里"哪些段落能被切开、切在第几个字"。**改 `MIN_SHELL_PARAGRAPH` 或 `splitPoint` 的 35%~65% 区间之前先跑它**——阈值定高了会出现"整页一条 shell 都没有"，而这种失灵在图上完全看不出来（页面只是长得像关掉了开关）。
6. 局部放大核对：`E:/Java/jdk-17/bin/java.exe -Dfile.encoding=UTF-8 .workbuddy/tools/PngCrop.java 源图.png 输出.png <x> <y> <宽> <高> [放大倍数]`。
   - 缩略图上根本看不清"文件名有没有被工具栏压住"，**别扫一眼整图就下结论**，裁一块放大看。这两个 bug（diff 卡片与 shell 块头部叠字）都是这么发现的。
   - 坐标要换算：输出的 PNG 是 **2x 缩放**（写 560 宽 → PNG 1120px 宽）；分布统计里的卡片 y 是**页面内**坐标，换算成像素要乘 2 再加顶栏高度；左侧栏展开时占 190 逻辑像素（= 380 像素），正文区从 x=380 起。

## GUI Designer（不要手改生成代码）
- `src/main/java/com/thief/idea/ui/SettingUi.java` 中的 `$$$setupUI$$$()` 方法和实例初始化块 `{}` 由 **IntelliJ GUI Designer** 依据同目录 `SettingUi.form` 生成，文件内明确标注 `DO NOT EDIT`。改 UI 必须用 IDEA 的 GUI Designer 编辑 `.form`，不要直接改生成代码。
- 唯一的例外记录：**"每页行数"下拉已从 1~3 手工扩展到 1~30**（`SettingUi.form` 的 `<model>` 与 `SettingUi.java` 的 `defaultComboBoxModel3` 两处同步改过，就是为了不破坏"用 Designer 打开再保存"的一致性）。后续若要再改这个下拉，请两处一起改。
- **要新增控件，不要去动 `.form` / `$$$setupUI$$$`**：照 `SettingUi` 构造函数里已有的做法，运行时给面板追加行——
  `FormLayout layout = (FormLayout) panel.getLayout(); layout.appendRow(new RowSpec("6dlu")); layout.appendRow(new RowSpec("center:default:grow"));`
  再用 `CellConstraints.xy(标签列, 新行号)` 添加。已经这么加的有：**TTS 热键**（Hotkeys 面板，落在第 11 行）、**代码语言下拉**（Style 面板，第 15 行）、**自定义模型名输入框**（Style 面板，第 17 行）、**Shell 代码块开关**（Style 面板，第 19 行，`xyw(2, 19, 3)` 横跨标签列）。
  - 行号 = 该面板 `.form` 里的 `rowspec` 条数 + 2。加之前先跑 `.workbuddy/tools/SettingPreview.java`，它会把每个控件占用的单元格打出来，确认没和既有控件抢同一格。
  - 手工加的下拉/输入框要**同步四处**：`SettingUi` 的字段与 `innit()` 回填、`Setting.isModified()`、`Setting.apply()`、`PersistentState` 的字段 + `getState/loadState` + 默认值兜底。旧限制 1~3 是给"没有滚动条的老阅读区"设的，现在有滚动条 + 助手排版，1 行根本不像一段回复。
- 第三方依赖 `org.apache.commons.lang.StringUtils`（commons-lang，非 lang3）与 `com.jgoodies.forms` 均随 IntelliJ Platform 提供（平台发行版自带的 jar），无需在 build.gradle 中额外添加依赖。

## 文件读取约束（MainUi.java）
- **编码自动检测**：`ensureCharset()`/`detectCharset()` 按"BOM → UTF-8 严格解码样本（尾部最多回退 4 字节重试）→ 回退 GB18030"检测编码并缓存于 `fileCharset` 字段；切书（refresh 的 `bookChanged` 分支）会重置缓存重新检测。GBK/ANSI/UTF-8（含 BOM，首行 `\uFEFF` 会被剥离）均支持；UTF-16 会报"暂不支持"。改动读取逻辑时不要绕过该检测，也不要写死 UTF-8。
- 翻页靠 `seekDictionary`（每 `cacheInterval=200` 行缓存一个文件指针）加速跳页；改动分页/跳页逻辑时注意维护该缓存。
- **读取走批量字节块**：`readLines()`/`appendLine()` 按 8KB 块读并切行（处理跨块残行、`\r\n`、末尾无换行行），`countLine()` 同样按字节块扫描换行符计数。新代码不要用 `RandomAccessFile.readLine()` 逐行读（慢且带 ISO-8859-1 往返）。

## 离线朗读（TTS，`src/main/java/com/thief/idea/tts/`）
- 目标：离线 TTS 逐页朗读小说，一页读完自动翻下一页。入口在 `MainUi` 顶部栏的喇叭按钮（点击 = 播放/停止，**右键** = Voice / Rate 菜单），朗读开关与页推进分别走 `startTts()`/`ttsNextPage()`，热键 `Ctrl+4`（设置页可改，`PersistentState.ttsKey`）；当前语音/语速在 `MainUi.ttsVoice` / `ttsRateValue` 字段里（原下拉框已删除，语音列表由 `populateTtsVoicesAsync()` 异步枚举后缓存于 `ttsVoices`）。
- **平台实现**：Windows 走 SAPI 的 `SpVoice` COM（`SapiVoice` + `WindowsSapiEngine`）；macOS 走 `/usr/bin/say`、Linux 走 `espeak-ng`（`CommandTtsEngine` 子类）。工厂/平台探测在 `TtsEngines`。
- **不要给 JNA 加依赖**：`com.sun.jna` 与 `com.sun.jna.platform.win32.COM.*`（`COMLateBindingObject` 等）随 IntelliJ Platform 的 `util-8.jar` 提供，`build.gradle` 无需声明，打出的 zip 也不应包含 jna。参考 `build.gradle` 注释。
- **COM 线程约束**：SAPI 是 STA，`SpVoice` 必须在同一线程创建/调用。`WindowsSapiEngine` 在 `TtsService` 的朗读线程上构造（构造时 `CoInitializeEx`），`pause()/resume()/stop()` 由其它线程只置 volatile 标志，实际 `Pause()/Resume()`/purge 在 `speak()` 轮询循环内执行。
- **完成判定**：`Speak` 用 `SPF_ASYNC`，再轮询 `Status.RunningState`（`SAPE` 枚举：0 等待/1 读完/2 朗读中）+ `WaitUntilDone`。`SapiVoice.runningState()` 每轮 fresh 取 `Status` 并用 `VariantClear` 释放，**不要**再对临时包装对象调用 `release()`（`COMBindingBaseObject(IDispatch)` 不 AddRef，会重复释放导致崩溃）。
- **语音切换**：SAPI 的 `Voice` 属性只支持 `PROPERTYPUTREF`，JNA 的 `setProperty` 用的是 `PROPERTYPUT` 会报成员不存在；`SapiVoice.putRefProperty()` 手工构造 `DISPPARAMS` 调用 `DISPATCH_PROPERTYPUTREF` 解决。
- 朗读与手动翻页共用 `MainUi` 的 `seek/currentPage/readBook()`，手动翻页/跳页/刷新/切书/老板键都会先 `stopTts()`；`readBook()`/`countSeek()` 已加 `synchronized` 防并发错乱。

## 其他约定
- `TestUi.java` 的 `isApplicable()` 恒返回 `false`，是禁用/实验代码，不要当作活跃入口。
- 代码注释与 UI 文案为中文，新增内容请保持一致。
- 无测试、无 lint / typecheck / formatter 配置；提交信息简短、中英文混用，无强制规范。
