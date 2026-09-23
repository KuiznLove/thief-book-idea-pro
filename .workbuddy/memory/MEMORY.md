# 项目长期记忆（thief-book-idea）

## 是什么
IntelliJ IDEA 插件的"摸鱼阅读器"，但从 v0.1.7 起**外观伪装成 AI 编码助手**（工具窗口 id `ai-assistant`，显示名 CodePilot），小说正文以"助手回复"呈现，掺入伪造的代码 diff 卡片与"段落中间的单行 shell 命令块"。技术细节与改动约定都写在仓库根 `AGENTS.md`（架构、交互映射、伪装残余、构建与预览命令），改代码前先看它。

## 本机环境（这台 Windows 工作机）
- JDK 17：`E:/Java/jdk-17`（`gradle.properties` 的 `org.gradle.java.home` 已指向它；CI 会删这一行）。
- Bash 缺 coreutils，`./gradlew` 不可用 → 用
  `E:/Java/jdk-17/bin/java.exe -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <task>`。
- 依赖下载走 Clash 代理 `127.0.0.1:7890`，需加 `-Dhttps.proxyHost/-Dhttps.proxyPort`（客户端与 `org.gradle.jvmargs` 都加）。
- Gradle Wrapper 8.4；构建用 IntelliJ Platform 2023.3（community），最低兼容 build 233。
- ⚠️ **2026-09-20 实测**：上述 JDK/代理路径在当前会话里都不通——`E:/Java/jdk-17` 不存在，PATH 里的 JDK 17 实际在 `D:/Program Files/java/jdk-17.0.7`。**代理恢复后构建是通过的**：用命令行 `-Dorg.gradle.java.home="D:/Program Files/java/jdk-17.0.7"` 覆盖 `gradle.properties` 里那条不存在的路径（不必改仓库文件），再补 `-Dhttps.proxyHost/-Dhttps.proxyPort`（客户端与 `org.gradle.jvmargs` 都要）；首次 buildPlugin 约 26 分钟（下 Gradle 8.4 + 平台 2023.3，之后缓存在 `~/.gradle`；缓存热了之后 ~26 秒）。
- ⚠️ **2026-09-23 实测**：`E:/JetBrains/Toolbox/IntelliJ IDEA Ultimate` 已升到 **IU-262**，且它的 `jbr/bin` 里**没有 `javap.exe`**（只有 `javac.exe`/`java.exe`）。查平台 API 签名改用 `"D:/Program Files/java/jdk-17.0.7/bin/javap.exe"` —— **本项目目标平台 2023.3 的 jar 是 Java 17 字节码**，JDK 17 的 javac/javap 都能读（"字节码 69 / 必须用 JBR"只适用于*已装 IDEA* 那套 2026 平台，两回事）。离线小工具编译同样用 JDK 17。
  - `javap` 的 `-cp "<目录>/*"` 通配符**不生效**，且**找不到类时静默返回空**——别把空输出当成"方法不存在"；要 `cd` 到 lib 目录用 `;` 拼 jar 名或显式列出 jar。
  - 想找"某个类在哪个 jar"：扫 `lib/*.jar` 的字节串最快，但那只证明**有引用**，要确认真有条目仍得解析 zip。

## 仓库约定
- **版本历史**：`481ca58`（first commit，重构前：MainUi 1881 行自带 IO）→ `78b42cb`（v0.3.3：抽出 `book/BookPager`+`BookSource`、`PersistentState` 迁移到平台 `XmlSerializer`、稳定性修复）→ `3498e14`（图标修复）。
- ⚠️ **本机 Git Bash 会吞掉 `git show <rev>^:<path>` 里的 `^`**（取到的是 `rev` 本身），会让人误判"某个改动早就存在"。先用 `git rev-parse <rev>^` 拿到哈希再取文件。
- 跑 `.workbuddy/tools/` 下的 PagerCheck / StateCheck（需要 platform lib + epublib + jsoup）：用 node 扫 `~/.gradle/caches/modules-2/files-2.1` 找 `ideaIC-2023.3`、`epublib*.jar`、`jsoup*.jar`，拼 classpath 后再加 `build/classes/java/main`。
- `BookPager.turnBack()` **有下限保护**（回退结果夹到 0），且 `readForwardLocked()` 只在 `currentPage > 0` 时写 `seekDictionary`——两条配套，删任一条都会让越界回退污染 0 号缓存、使之后的 `jumpTo` 整体错位一整页（v0.3.4 已修，`.workbuddy/tools/PagerEdgeCheck.java` 是这对约定的守门用例）。用户路径另有 `MainUi` 的 `currentLine()/lineCount <= 1` 守卫。
- **发布（GitHub Release）**：升版本号两处（`build.gradle` + `plugin.xml`）→ README/change-notes → `buildPlugin` + `zipcheck` → 明确路径 `git add` → commit → `git tag v<版本>` → push。**push 必须带代理**（`-c http.proxy=127.0.0.1:7890 -c https.proxy=...`，直连会报 SSL unexpected eof）；**本机无 gh CLI / 无 GITHUB_TOKEN**，建 Release 走 REST API，token 用 `git credential fill` 取。完整步骤见 AGENTS.md「发布」一节。
- 对比 UiPreview 预览图时要**留意鼠标位置**：`GlyphButton` 的悬停底色会让某个按钮区域出现几千像素差异，同一份代码重跑即可排除。
- 源码文件统一 **CRLF**；新增文件请保持 CRLF。
- **打包不要覆盖已有的包**：`buildPlugin` 产物名只由版本号决定，同名会直接覆盖 `build/distributions/thief-book-idea-<ver>.zip`。重新打包前先把已存在的同名 zip 改名备份（加时间戳），旧包必须保留——用户明确要求过"打新的包不要动旧的包"；出包后再留一份新包的时间戳副本，便于日后回溯"哪一版是哪一个"。
- **图标的 HiDPI 铁律**（2026-09-23 定型，v0.3.4 的"图标糊"就是踩反了）：`AssistantIcons$SvgIcon` 自己渲染 SVG —— ① 矢量层面把源码里的 `#000000` 换成目标色（比"画完再按 alpha 染色"边缘更干净）；② 按 **`size × 绘制时 Graphics 的缩放倍数`** 光栅化；③ 落笔**仍用逻辑尺寸 `size`**（`g.drawImage(raster, x, y, size, size, null)`），使位图与设备像素 1:1。
  - 这样"Graphics 已缩放"（JRE HiDPI）与"未缩放"两种模式都对，**不用判断当前是哪种**。
  - ❌ 反面做法：生成 `size` px 位图交给 `ImageIcon` → 2x 屏上被 Swing 插值放大成 `2×size` 设备像素 → 糊。也别退回 `IconLoader.getIcon`（离线工具里无 Application，它根本解析不出图标，`getIconWidth()` 返回 0）。
  - 清晰度必须**量化**：跑 `.workbuddy/tools/IconHiDpiCheck.java <sysScale> <图标名> <尺寸>`（与参考图逐像素比对，两种模式都该 0 差异）；肉眼判断必须 `PngCrop` 裁图放大，缩略图看不出来。
- 界面文案与注释用中文；UI 配色必须走 `AssistantTheme` 的 JBColor 配对，禁止写死颜色。
- `SettingUi.form` / `SettingUi.java` 是 GUI Designer 生成物，一般不要手改；唯一例外是"每页行数"下拉（1~30）两处同步手工扩展过，改它要一起改。
- **卡片头部（diff 卡片与 shell 块）是同一套结构**：`SH`/`PY`/`JAVA` 角标 + 文件名 + 复制/导出/刷新 + 主按钮（Apply / Run）。改头部要两边一起改，布局都走 `AssistantPageView.layoutCardHeader()`。
  - 窄宽度三级降级：收窄文件名 → 收起三个次要图标（只留主按钮）→ 整条工具栏隐藏。阈值用的是 `CardToolbar` **构造时缓存**的 `fullWidth` / `primaryWidth`——不能用 `toolbar.getPreferredSize()`（FlowLayout 会跳过不可见组件，会来回抖动）。
  - 改完必须用 `preview-narrow.png` + `PngCrop` 放大核对，别只看缩略图（名字被工具栏压住在小图上看不出来）。
- **同一条横栏只用一条 `AssistantTheme.RowLayout`**（2026-09-23 定型，用户报"两行图标从高度上没对齐"）：顶部栏与底部输入框工具栏原来都是"左 `FlowLayout` + 右 `FlowLayout` + 中间留白"的两段式，`FlowLayout` 只按**自己那一行**的最高元素居中，两侧标高不同就错开中心线（用户 HiDPI 环境实测顶栏差 ~6 设备像素、底栏 ~13）。改成一条 `RowLayout`：所有子组件对齐容器中线，`RowLayout.spring()` 零宽弹簧推右侧组，间距用 `gapBefore(c, px)`；**首个组件的左侧间距要显式给**（`leadingGap` 默认 0，为保留旧 FlowLayout 的 hgap），右侧 border 的 right 由 1×hgap 补成 2×hgap。
  - ⚠️ 这类问题在本机 1x/1.5x 离线**复现不出来**（`HeaderProbe` 1.5x 下全部对齐）——"我本地渲染看着没问题"不能作为结案依据，要按根因消除。
- `buildPlugin` 偶发 `~/.gradle/caches/journal-1/journal-1.lock (拒绝访问)`（`--stop` 显示无 daemon 也会出现，疑似句柄/杀软残留）：**加 `--no-daemon` 重试即可**（实测 53s 通过），别去删 lock 文件。

## 可复用小工具（都在 `.workbuddy/tools/`）
- `UiPreview.java`：离线把伪装面板渲染成 PNG（无需启动 IDE），用于排版自查；一次输出 8 张——`preview.png`（Python/侧栏展开）、`preview-collapsed.png`（收起）、`preview-long.png`（长页）、`preview-java.png`、`preview-vue.png`、`preview-model.png`（超长自定义模型名，查输入框右下角会不会被撑变形）、`preview-noshell.png`（关掉 shell 开关，与 `-long` 同画布便于对照）、`preview-narrow.png`（固定 400px + 侧栏展开，专查卡片头部降级），并打印每张卡/每条 shell 块的 y/百分比分布。**输出图是 2x 的**。
  - **跑它必须带 `-Dfile.encoding=UTF-8`**（工具是单文件源码启动，javac 按平台默认 GBK 读 UTF-8 源文件 → 中文乱码 + `\n` 转义被吞 → 样本拆错、卡片分布统计全错）。启动首行 `[sample] ... lines=16` 可用来核对。
  - 还得加 `--add-exports=java.desktop/sun.font=ALL-UNNAMED`。
- `PngCrop.java`：从预览 PNG 里裁一块放大，用于核对缩略图看不清的细节（文件名有没有被压住、降级有没有生效）。**输出图是 2x 缩放**，坐标换算见 AGENTS.md / 文件头注释。
- `shellslots.js`：复算 shell 块的落点（哪些段落会被切开、切在第几字）。改 `MIN_SHELL_PARAGRAPH` / 切点区间前先跑，防止阈值定高导致"整页一条都没有"。
- `zipcheck.js`：解出 `build/distributions/*.zip` 内层 jar，用字符串比对确认打出来的包含的是**新版**素材（而不是旧编译产物），并打印包内 `plugin.xml` 版本。换素材后出包做一次很有用。
  - ⚠️ class 常量池里是 **modified UTF-8**：中文 needle 必须先 `Buffer.from(n,'utf8').toString('latin1')` 再比对，直接 `includes(中文)` 永远匹配不上（脚本里已有 `inClass()` 封装）。
  - ⚠️ **内部类是独立 class 文件**：查 `AssistantIcons` 的新实现标志要去 `AssistantIcons$SvgIcon.class`，在 `AssistantIcons.class` 里找 `SVGLoader` 永远找不到（踩过）。
- `IconGen.java`：生成工具窗口图标 `icons/assistant.png` / `icons/assistant@2x.png`。
- `SettingPreview.java`：打印 `SettingUi` 各控件占用的 FormLayout 单元格，验证运行时追加的控件没抢格。
- `SelectCheck.java`：离线渲染一页后遍历所有文本组件，逐个建选区读回选中文字，打印"可选中 N / 不可选中 M"。**"正文选不中"在截图里完全看不出来**，改阅读区文本组件后跑一次。
- `PngDiff.java`：两张预览 PNG 的像素级差异报告（差异像素数 / 包围盒 / 按 y 聚类的"差异带"）。用来证明"排版零变化"：改动前后各跑一遍 `UiPreview` 再 diff。实测能指出"某行整体位移 2px"；换图标后实测差异只剩 4 条 15px 高的横带（卡片头部工具栏），说明底板与文字分毫未动。
- `IconHiDpiCheck.java`（2026-09-23 新增）：图标清晰度的**量化**检查。把 `AssistantIcons` 的图标画到画布上，与"同一渲染器按目标像素尺寸渲染的参考图"逐像素比对，分别测 `plain`（Graphics 未缩放）与 `scaled`（已按设备倍数缩放）两种模式——**都该 0 差异**；只对一个说明实现依赖了某个具体的 HiDPI 模式。用法 `... IconHiDpiCheck.java <sysScale> <图标名> <逻辑尺寸>`。
  - 参考图必须**先换成和被测图标一样的颜色**再渲染，否则比出来的是颜色差（踩过：一开始忘了换色，差异 65%）。
  - 想量化"旧版有多糊"：从旧安装包里解出内层 jar 的 `com/thief/idea/**/*.class` 到临时目录，拿它当 `-cp` 跑同一个工具（比 `git show` 可靠——HEAD 未必等于那个中间版本）。
- `lab.py`（2026-09-23 新增）：**离线工具的编排脚本**——本机 Git Bash 缺 coreutils（`rm`/`ls`/`cat`/`tail`/`dirname` 全 127），凡"删目录 / 串命令 / 拼 classpath"都走它：`compile`（编 `src/main/java` 到 `build/classes/java/main`，改完源码想跑任何离线工具先跑这个）/ `preview <out.png> [w] [h]` / `ink <png> [阈值] [间隙] [前缀] [y0] [y1] [light|dark]` / `band <png> <y0> <y1> [...]` / `header <scale> [宽] [out] [字体倍率]`。
  - classpath 里**必须含 `src/main/resources`**（或 `build/resources/main`），否则 `icons/lucide/*.svg` 加载不到、预览图图标整片空白，量测时表现为"这一列没墨迹"，容易误判成图标没画出来。
- `HeaderProbe.java`（2026-09-23 新增）：**横栏对齐探针**。用真实组件复刻 `MainUi.initHeaderBar()` 与 `ChatInputBar` 工具栏行，打印每个控件的 bounds/垂直中心线（设备像素）与"图标墨迹中心 vs 图标框中心"偏移，并画 PNG。判据：同一条栏内所有控件中线一致，残差应 ≤0.5px（奇偶取整）。
  - ⚠️ **`JBUIScale` 的坑**：只调 `setSystemScaleFactor(SCALE)` **无效**（`scale()` 用的还是预计算值），必须再调 `setUserScaleFactorForTest(SCALE)`，否则永远按 1x 打印（表现为"302 → 302"）。
  - `-Dprobe.fontScale=<倍率>` 模拟真实 IDE 的字体放大（离线时 `JBUI.Fonts` 不会自动跟随）；字体放大后可能触发 `sun.font.FontUtilities` 的 `IllegalAccessError`，要带 `--add-exports=java.desktop/sun.font=ALL-UNNAMED`。
- `InkProfile.java` 的两个易错参数：`y0 y1` 把量测限制在一条横带里（**整张预览图不分带直接量没意义**——列聚类会把顶栏和正文混成一列）；末尾 `light|dark` 选墨迹极性，**离线预览是亮色主题必须传 `light`**（墨迹取暗像素）且阈值要抬到 ~200，否则浅灰的次要色图标会被漏掉、量出来只剩两三个元素。
- `LayoutProbe.java`（2026-09-23 新增）：一次性探针，用来排除"`FlowLayout` 在自己容器里居中算错"这个假设（**等高容器下它是准的**），留着备查。
- `release.js`（2026-09-23 新增）：**发版一条命令搞定**——建 GitHub Release + 上传安装包。
  `RELEASE_PROXY=http://127.0.0.1:7890 node .workbuddy/tools/release.js build/distributions/thief-book-idea-<ver>.zip .workbuddy/preview/v<ver>-notes.md v<ver> "v<ver>"`
  - 必须用 `curl.exe` 子进程，**别改成 Node 的 `fetch`**：undici 不读 `HTTP_PROXY`/`HTTPS_PROXY`（Node 22 无 `--use-env-proxy`），直连 github 会 `OpenSSL SSL_read: unexpected eof`。
  - token 走 `git credential fill` 取，**只经 stdin** 交给 curl 的 `--config -`（不进 argv、不打印、不落盘）；curl 配置文件里**不能有反斜杠**，路径统一换成 `/`。
  - ⚠️ 上传完立刻回读 release 可能是 `assets: []`（GitHub API `max-age=60`，代理喂回"刚创建、还没附件"的缓存）——以 `asset-resp.json` 的 `state=uploaded` 为准，或按 release **id** + `Cache-Control: no-cache` 复查。
  - 发布说明文件（`v<ver>-notes.md`）放在 `.workbuddy/preview/`（已 gitignore），格式沿用 `v0.3.4-notes.md`。
