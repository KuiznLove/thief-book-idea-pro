# 项目长期记忆（thief-book-idea）

## 是什么
IntelliJ IDEA 插件的"摸鱼阅读器"，但从 v0.1.7 起**外观伪装成 AI 编码助手**（工具窗口 id `ai-assistant`，显示名 CodePilot），小说正文以"助手回复"呈现，掺入伪造的代码 diff 卡片与"段落中间的单行 shell 命令块"。技术细节与改动约定都写在仓库根 `AGENTS.md`（架构、交互映射、伪装残余、构建与预览命令），改代码前先看它。

## 本机环境（这台 Windows 工作机）
- JDK 17：`E:/Java/jdk-17`（`gradle.properties` 的 `org.gradle.java.home` 已指向它；CI 会删这一行）。
- Bash 缺 coreutils，`./gradlew` 不可用 → 用
  `E:/Java/jdk-17/bin/java.exe -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <task>`。
- 依赖下载走 Clash 代理 `127.0.0.1:7890`，需加 `-Dhttps.proxyHost/-Dhttps.proxyPort`（客户端与 `org.gradle.jvmargs` 都加）。
- Gradle Wrapper 8.4；构建用 IntelliJ Platform 2023.3（community），最低兼容 build 233。
- ⚠️ **2026-09-20 实测**：上述 JDK/代理路径在当前会话里都不通——`E:/Java/jdk-17` 与 `~/.gradle`（wrapper dists / caches）都不存在，PATH 里的 JDK 17 实际在 `D:/Program Files/java/jdk-17.0.7`。**代理恢复后构建是通过的**：用命令行 `-Dorg.gradle.java.home="D:/Program Files/java/jdk-17.0.7"` 覆盖 `gradle.properties` 里那条不存在的路径（不必改仓库文件），再补 `-Dhttps.proxyHost/-Dhttps.proxyPort`（客户端与 `org.gradle.jvmargs` 都要）；首次 buildPlugin 约 26 分钟（下 Gradle 8.4 + 平台 2023.3，之后缓存在 `~/.gradle`）。**代理没开时不必卡住**：用 IDEA 自带运行时做编译校验即可——
  `E:/JetBrains/Toolbox/IntelliJ IDEA Ultimate/jbr/bin/javac.exe`（javac 25，平台 class 是字节码 69，JDK 17/21 读不了）+ `-cp "E:/JetBrains/Toolbox/IntelliJ IDEA Ultimate/lib/*"`；跑离线工具用 `jbr/bin/java.exe` + 平台目录的 `lib/*`。做法与排除清单见仓库 `AGENTS.md` 的"没有 Gradle 缓存 / 代理不可用时的替代校验"。

## 仓库约定
- **版本历史**：`481ca58`（first commit，重构前：MainUi 1881 行自带 IO）→ `78b42cb`（v0.3.3：抽出 `book/BookPager`+`BookSource`、`PersistentState` 迁移到平台 `XmlSerializer`、稳定性修复）→ `3498e14`（图标修复）。要做"重构前后对比"就以 481ca58 为基线。
- ⚠️ **本机 Git Bash 会吞掉 `git show <rev>^:<path>` 里的 `^`**（取到的是 `rev` 本身），会让人误判"某个改动早就存在"。先用 `git rev-parse <rev>^` 拿到哈希再取文件。
- 跑 `.workbuddy/tools/` 下的 PagerCheck / StateCheck（需要 platform lib + epublib + jsoup）：用 node 扫 `~/.gradle/caches/modules-2/files-2.1` 找 `ideaIC-2023.3`、`epublib*.jar`、`jsoup*.jar`，拼 classpath 后再加 `build/classes/java/main`。
- `BookPager.turnBack()` 目前**没有下限保护**：越界上一页会把 `currentPage` 弄成负数，并污染 `seekDictionary[0]`，使之后的 `jumpTo` 整体错位（实测 `jumpTo(10)` 返回 L21）。用户路径被 `MainUi` 的 `currentLine()/lineCount <= 1` 守卫挡住，改分页逻辑时留意——证据见 `.workbuddy/tools/PagerEdgeCheck.java`。
- 源码文件统一 **CRLF**；新增文件请保持 CRLF。
- 界面文案与注释用中文；UI 配色必须走 `AssistantTheme` 的 JBColor 配对，禁止写死颜色。
- `SettingUi.form` / `SettingUi.java` 是 GUI Designer 生成物，一般不要手改；唯一例外是"每页行数"下拉（1~30）两处同步手工扩展过，改它要一起改。
- **卡片头部（diff 卡片与 shell 块）是同一套结构**：`SH`/`PY`/`JAVA` 角标 + 文件名 + 复制/导出/刷新 + 主按钮（Apply / Run）。改头部要两边一起改，布局都走 `AssistantPageView.layoutCardHeader()`。
  - 窄宽度三级降级：收窄文件名 → 收起三个次要图标（只留主按钮）→ 整条工具栏隐藏。阈值用的是 `CardToolbar` **构造时缓存**的 `fullWidth` / `primaryWidth`——不能用 `toolbar.getPreferredSize()`（FlowLayout 会跳过不可见组件，会来回抖动）。
  - 改完必须用 `preview-narrow.png` + `PngCrop` 放大核对，别只看缩略图（名字被工具栏压住在小图上看不出来）。

## 可复用小工具（都在 `.workbuddy/tools/`）
- `UiPreview.java`：离线把伪装面板渲染成 PNG（无需启动 IDE），用于排版自查；一次输出 8 张——`preview.png`（Python/侧栏展开）、`preview-collapsed.png`（收起）、`preview-long.png`（长页）、`preview-java.png`、`preview-vue.png`、`preview-model.png`（超长自定义模型名，查输入框右下角会不会被撑变形）、`preview-noshell.png`（关掉 shell 开关，与 `-long` 同画布便于对照）、`preview-narrow.png`（固定 400px + 侧栏展开，专查卡片头部降级），并打印每张卡/每条 shell 块的 y/百分比分布。
  - **跑它必须带 `-Dfile.encoding=UTF-8`**（工具是单文件源码启动，javac 按平台默认 GBK 读 UTF-8 源文件 → 中文乱码 + `\n` 转义被吞 → 样本拆错、卡片分布统计全错）。启动首行 `[sample] ... lines=16` 可用来核对。
  - 还得加 `--add-exports=java.desktop/sun.font=ALL-UNNAMED`。
- `PngCrop.java`：从预览 PNG 里裁一块放大，用于核对缩略图看不清的细节（文件名有没有被压住、降级有没有生效）。**输出图是 2x 缩放**，坐标换算见 AGENTS.md / 文件头注释。
- `shellslots.js`：复算 shell 块的落点（哪些段落会被切开、切在第几字）。改 `MIN_SHELL_PARAGRAPH` / 切点区间前先跑，防止阈值定高导致"整页一条都没有"。
- `zipcheck.js`：解出 `build/distributions/*.zip` 内层 jar，用字符串比对确认打出来的包含的是**新版**素材（而不是旧编译产物），并打印包内 `plugin.xml` 版本。换素材后出包做一次很有用。
  - ⚠️ class 常量池里是 **modified UTF-8**：中文 needle 必须先 `Buffer.from(n,'utf8').toString('latin1')` 再比对，直接 `includes(中文)` 永远匹配不上（脚本里已有 `inClass()` 封装）。
- `IconGen.java`：生成工具窗口图标 `icons/assistant.png` / `assistant@2x.png`。
- `SettingPreview.java`：打印 `SettingUi` 各控件占用的 FormLayout 单元格，验证运行时追加的控件没抢格。
- `SelectCheck.java`（2026-09-20 新增）：离线渲染一页后遍历所有文本组件，逐个建选区读回选中文字，打印"可选中 N / 不可选中 M"。**"正文选不中"在截图里完全看不出来**，改阅读区文本组件后跑一次。
- `PngDiff.java`（2026-09-20 新增）：两张预览 PNG 的像素级差异报告（差异像素数 / 包围盒 / 按 y 聚类的"差异带"）。做法：`git show HEAD:<路径> > 临时目录/x.java` 取旧版源码，与当前其它源文件一起编译到另一个输出目录，各跑一遍 `UiPreview` 再 PngDiff 对比，用来证明"排版零变化"。实测它能指出"换成 JTextField 后某行整体位移 2px"这种肉眼看不出的问题。
