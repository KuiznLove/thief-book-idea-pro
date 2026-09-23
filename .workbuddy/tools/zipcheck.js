// 校验 build/distributions 下产物 zip：内层 jar 里的 DisguiseContent.class 是否含最新 Java 素材常量
const fs = require('fs'), zlib = require('zlib'), os = require('os'), path = require('path');

function readZipBuffer(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) { if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; } }
  if (eocd < 0) throw new Error('no EOCD');
  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const entries = [];
  for (let n = 0; n < count; n++) {
    const nameLen = buf.readUInt16LE(off + 28), extraLen = buf.readUInt16LE(off + 30), cmtLen = buf.readUInt16LE(off + 32);
    const name = buf.toString('utf8', off + 46, off + 46 + nameLen);
    const lho = buf.readUInt32LE(off + 42);
    entries.push({ name, lho });
    off += 46 + nameLen + extraLen + cmtLen;
  }
  function data(lho) {
    const nameLen = buf.readUInt16LE(lho + 26), extraLen = buf.readUInt16LE(lho + 28);
    const start = lho + 30 + nameLen + extraLen;
    const cs = buf.readUInt32LE(lho + 18);
    const method = buf.readUInt16LE(lho + 8);
    const raw = buf.slice(start, start + cs);
    return method === 8 ? zlib.inflateRawSync(raw) : raw;
  }
  return { entries, data };
}

const outer = readZipBuffer(fs.readFileSync(process.argv[2]));
const inner = outer.entries.find(e => /instrumented-thief-book-idea.*\.jar$/.test(e.name));
if (!inner) throw new Error('inner jar not found');
console.log('inner jar:', inner.name);

const innerJar = readZipBuffer(outer.data(inner.lho));
const cls = innerJar.entries.find(e => /DisguiseContent\.class$/.test(e.name));
if (!cls) throw new Error('DisguiseContent.class not found');

// 顺带核对内层 jar 里的 plugin.xml 版本号，确认打出来的包确实是这一版
const px = innerJar.entries.find(e => e.name === 'META-INF/plugin.xml');
if (px) {
  const xml = innerJar.data(px.lho).toString('utf8');
  console.log('plugin.xml: ' + (xml.match(/<version>[^<]+<\/version>/) || ['?'])[0]
    + ' | ' + (xml.match(/<name>[^<]+<\/name>/) || ['?'])[0]
    + ' | since-build=' + ((xml.match(/since-build="([^"]*)"/) || [, '?'])[1]));
}

const bytes = innerJar.data(cls.lho);
const text = bytes.toString('latin1');
const has = s => text.includes(Buffer.from(s, 'utf8').toString('latin1'));

console.log('\n--- 新版（Spring Boot 智能客服）应存在 ---');
for (const c of ['chat/ChatController.java', 'chat/ChatService.java', 'chat/IntentResolver.java',
  'faq/FaqRepository.java', 'chat/SessionStore.java', 'agent/TransferService.java',
  'config/WebSocketConfig.java', '/api/chat', '/ws/chat', 'agent.queue-timeout']) {
  console.log((has(c) ? '  OK  ' : ' MISS ') + c);
}
console.log('\n--- 旧版（图计算/深度学习）应消失 ---');
// 只查 Java 素材（v0.2.1 换成了 Spring Boot 智能客服）。
// 不要拿 'model.eval()' 之类的 PyTorch 行来判断——Python 素材本来就是图计算风格，从来没换过
for (const c of ['HyperGraph.java', 'Trainer.java', 'OnnxExporter.java', 'FocalLoss.java']) {
  console.log((has(c) ? ' STALE' : '  ok  ') + c);
}

// 注意：class 常量池里的字符串是 modified UTF-8，中文 needle 必须先按 UTF-8 编码再转 latin1，
// 直接 includes(中文) 永远匹配不上（ASCII needle 才碰巧能比）
const inClass = (t, needle) => t.includes(Buffer.from(needle, 'utf8').toString('latin1'));

// 自定义模型（v0.2.2）+ 单行 shell 块（v0.3.0）+ shell 块完整头部与卡片头部降级（v0.3.1）
console.log('\n--- 自定义模型 / shell 块 / 卡片头部 ---');
const guards = [
  ['PersistentState', 'customModel'],
  ['ui/SettingUi', 'customModel'],
  ['ui/ChatInputBar', 'customModel'],
  ['PersistentState', 'shellBlockEnabled'],
  ['ui/SettingUi', 'Shell snippet in paragraphs'],
  ['ui/AssistantPageView', 'thief.shellIndex'],
  ['disguise/DisguiseContent', 'git status --short'],
  ['disguise/DisguiseContent', 'kubectl get pods -n staging'],
  // v0.3.1：shell 素材从 String[] 改成 ShellScript[]（文件名 + 命令配对），头部与 diff 卡片同构
  ['disguise/DisguiseContent', 'SHELL_SCRIPTS'],
  ['disguise/DisguiseContent', 'scripts/build.sh'],
  ['disguise/DisguiseContent', '已在终端运行'],
  ['ui/AssistantPageView', 'layoutCardHeader'],
  ['ui/AssistantPageView', 'CardToolbar'],
];
for (const [clsName, needle] of guards) {
  const entry = innerJar.entries.find(e => e.name.endsWith(clsName + '.class'));
  if (!entry) { console.log(' MISS ' + clsName + '.class'); continue; }
  const t = innerJar.data(entry.lho).toString('latin1');
  console.log((inClass(t, needle) ? '  OK  ' : ' MISS ') + clsName + ' 含 "' + needle + '"');
}

// v0.3.1 替换掉的旧实现：确认没跟着打进去
console.log('\n--- v0.3.1 旧实现应消失 ---');
const gone = [
  ['disguise/DisguiseContent', 'SHELL_COMMANDS'],
  ['ui/AssistantPageView', 'shellLine'],
];
for (const [clsName, needle] of gone) {
  const entry = innerJar.entries.find(e => e.name.endsWith(clsName + '.class'));
  const t = entry ? innerJar.data(entry.lho).toString('latin1') : '';
  console.log((inClass(t, needle) ? ' STALE' : '  ok  ') + clsName + ' 不含 "' + needle + '"');
}

// v0.3.2：只读文本可选中可复制（makeSelectable 统一配置 + 单行 SelectableText + 复制/全选右键菜单
// + 初始焦点交回输入框）。旧实现里正文设了 setFocusable(false)，所以选不中——那行代码不在了，
// 但"不在了"没法用字符串稳定判定，这里只正向确认新实现存在
console.log('\n--- v0.3.2 可选中可复制 / 初始焦点 ---');
const selectableCls = innerJar.entries.find(e => e.name === 'com/thief/idea/ui/AssistantTheme$SelectableText.class');
console.log((selectableCls ? '  OK  ' : ' MISS ') + 'ui/AssistantTheme$SelectableText.class');
const v032 = [
  ['ui/AssistantTheme', 'makeSelectable'],
  ['ui/AssistantTheme', 'SelectableText'],
  ['ui/AssistantTheme', '复制'],          // 右键菜单文案
  ['ui/AssistantTheme', '全选'],
  // 注意 makeSelectable 的调用点在内部类里（段落面板 AssistantPageView$ParagraphPane），
  // AssistantPageView.class 里只有对 SelectableText 的类引用；endsWith 比对内部类的 $ 名字也成立
  ['ui/AssistantPageView$ParagraphPane', 'makeSelectable'],
  ['ui/AssistantPageView', 'SelectableText'],
  ['ui/ChatInputBar', 'inputComponent'],
  ['MainUi', 'setPreferredFocusableComponent'],
];
for (const [clsName, needle] of v032) {
  const entry = innerJar.entries.find(e => e.name.endsWith(clsName + '.class'));
  if (!entry) { console.log(' MISS ' + clsName + '.class'); continue; }
  const t = innerJar.data(entry.lho).toString('latin1');
  console.log((inClass(t, needle) ? '  OK  ' : ' MISS ') + clsName + ' 含 "' + needle + '"');
}

// v0.3.5：图标改用 Lucide 开源素材 + 按设备像素自绘（HiDPI 不糊）；
// 顶部栏与底部工具栏由"左右两个 FlowLayout"改成单行 RowLayout（左右两组共用一条中心线）
console.log('\n--- v0.3.5 图标素材 / RowLayout 对齐 ---');
const v035Classes = [
  'com/thief/idea/ui/AssistantIcons$SvgIcon.class',
  'com/thief/idea/ui/AssistantTheme$RowLayout.class',
  'com/thief/idea/ui/AssistantTheme$RowLayout$Spring.class',
];
for (const c of v035Classes) {
  console.log((innerJar.entries.some(e => e.name === c) ? '  OK  ' : ' MISS ') + c);
}
const v035 = [
  // 图标：从资源加载 SVG + 按绘制时的变换倍数自己光栅化。
  // ⚠️ SVGLoader / getTransform 都写在内部类 SvgIcon 里（内部类是独立 class 文件），
  // 在 AssistantIcons.class 里找永远是 MISS——这里必须写 $SvgIcon
  ['ui/AssistantIcons', 'icons/lucide/'],
  ['ui/AssistantIcons$SvgIcon', 'SVGLoader'],
  ['ui/AssistantIcons$SvgIcon', 'getTransform'],
  // 对齐：单行布局 + 内置弹簧 + 前置间距（同样在内部类 RowLayout 里）
  ['ui/AssistantTheme', 'RowLayout'],
  ['ui/AssistantTheme$RowLayout', 'gapBefore'],
  ['ui/AssistantTheme$RowLayout', 'thief.rowGapBefore'],
  ['ui/AssistantTheme$RowLayout', 'spring'],
  ['MainUi', 'RowLayout'],
  ['MainUi', 'gapBefore'],
  ['ui/ChatInputBar', 'RowLayout'],
  ['ui/ChatInputBar', 'gapBefore'],
];
for (const [clsName, needle] of v035) {
  const entry = innerJar.entries.find(e => e.name.endsWith(clsName + '.class'));
  if (!entry) { console.log(' MISS ' + clsName + '.class'); continue; }
  const t = innerJar.data(entry.lho).toString('latin1');
  console.log((inClass(t, needle) ? '  OK  ' : ' MISS ') + clsName + ' 含 "' + needle + '"');
}

// v0.3.5 替换掉的旧实现：图标不该再走"画完按 alpha 染色"（SrcIn），
// 也不该再有把 FontMetrics 搬来搬去的旧图标工厂
console.log('\n--- v0.3.5 旧实现应消失 ---');
{
  const entry = innerJar.entries.find(e => e.name === 'com/thief/idea/ui/AssistantIcons.class');
  const t = entry ? innerJar.data(entry.lho).toString('latin1') : '';
  for (const needle of ['SrcIn', 'AlphaComposite']) {
    console.log((inClass(t, needle) ? ' STALE' : '  ok  ') + 'ui/AssistantIcons 不含 "' + needle + '"');
  }
}

// 图标素材必须真的打进包里（少一个图标就会在界面上显示为空白，而"空白"在预览图上不明显）
const lucide = innerJar.entries.filter(e => /^icons\/lucide\/.+\.svg$/.test(e.name));
console.log('\n--- 图标素材 ---');
console.log('  lucide svg 共 ' + lucide.length + ' 个' + (lucide.length >= 14 ? '  OK' : '  MISS'));
console.log((innerJar.entries.some(e => e.name === 'icons/lucide/LICENSE') ? '  OK  ' : ' MISS ') + 'icons/lucide/LICENSE');

