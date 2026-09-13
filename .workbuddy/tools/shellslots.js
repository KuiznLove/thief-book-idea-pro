// 统计 UiPreview 里两个样本的段落长度分布，用来定 MIN_SHELL_PARAGRAPH
const fs = require('fs');
const src = fs.readFileSync('.workbuddy/tools/UiPreview.java', 'utf8');

function extract(name) {
  const start = src.indexOf(name + ' =');
  const end = src.indexOf('";', start);
  const chunk = src.slice(start, end + 1);
  const lits = [...chunk.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map(m => m[1]);
  return lits.join('').replace(/\\n/g, '\n').replace(/\\"/g, '"');
}

// 与 AssistantPageView.splitPoint 完全一致
function splitPoint(text) {
  const length = text.length;
  const from = Math.max(1, Math.trunc(length * 0.35));
  const to = Math.min(length - 1, Math.trunc(length * 0.65));
  const middle = Math.trunc(length / 2);
  let best = -1;
  for (let i = from; i <= to; i++) {
    const prev = text.charAt(i - 1);
    if (!'。！？；…'.includes(prev)) continue;
    if (best < 0 || Math.abs(i - middle) < Math.abs(best - middle)) best = i;
  }
  return best;
}

for (const name of ['SAMPLE', 'LONG_SAMPLE']) {
  const paragraphs = extract(name).split('\n').filter(s => s.length > 0);
  console.log(`\n${name}: ${paragraphs.length} 段`);
  console.log('  段长:', paragraphs.map((s, i) => `p${i + 1}=${s.length}`).join(' '));
  for (const min of [36, 40, 45, 50, 60]) {
    const candidates = [];
    paragraphs.forEach((text, i) => { if (text.length >= min && splitPoint(text) > 0) candidates.push(i + 1); });
    console.log(`  MIN=${min} → 候选段 ${candidates.length} 个: [${candidates.join(', ')}]`);
  }
}

// 打印当前取值下每个候选段的切点，确认"切出来是两截、而不是半行对半行"
console.log('\n[MIN=40 / 35%~65%] 切点明细');
for (const name of ['SAMPLE', 'LONG_SAMPLE']) {
  const paragraphs = extract(name).split('\n').filter(s => s.length > 0);
  paragraphs.forEach((text, i) => {
    if (text.length < 40) return;
    const cut = splitPoint(text);
    if (cut > 0) console.log(`  ${name} p${i + 1}: ${text.length} 字 → 切在 ${cut}，两半 ${cut} + ${text.length - cut}`);
  });
}
