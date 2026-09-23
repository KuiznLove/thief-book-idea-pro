#!/usr/bin/env node
// 发布 GitHub Release 并上传安装包。
//
// 为什么不用 gh：本机没装 gh CLI，环境变量里也没有 GITHUB_TOKEN，所以走 GitHub REST API。
//
// 为什么用 curl 子进程而不是 fetch：Node 的 fetch（undici）**不读 HTTP_PROXY/HTTPS_PROXY**
// （Node 22 还没有 --use-env-proxy），而本机直连 github 会 `OpenSSL SSL_read: unexpected eof`。
// 本机 `C:/Windows/System32/curl.exe` 走 Clash 代理是验证过的，所以用它。
//
// token 从 Windows 凭据管理器取（`git credential fill`），并且**只通过 stdin 交给 curl**
// （`--config -`）：不进 argv、不打印、不落盘。
//
// 用法：
//   node .workbuddy/tools/release.js <zip> <notes.md> [tag] [release 标题]
// 例：
//   PROXY=http://127.0.0.1:7890 node .workbuddy/tools/release.js \
//     build/distributions/thief-book-idea-0.3.5.zip .workbuddy/preview/v0.3.5-notes.md v0.3.5 "v0.3.5"
//
// 响应留档到 .workbuddy/preview/：release-resp.json / asset-resp.json / release-id.txt / payload.json
// （该目录已被 .gitignore 忽略）

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const REPO = 'KuiznLove/thief-book-idea-pro';
const CURL = 'C:/Windows/System32/curl.exe';
const ROOT = path.resolve(__dirname, '..', '..');
const ARCHIVE = path.join(ROOT, '.workbuddy', 'preview');
const PROXY = process.env.RELEASE_PROXY || process.env.PROXY || 'http://127.0.0.1:7890';

/** curl 配置文件里不要出现反斜杠（配置解析器会吃转义），统一成正斜杠 */
const curlPath = p => p.replace(/\\/g, '/');

function readToken() {
  const out = execFileSync('git', ['credential', 'fill'], {
    cwd: ROOT,
    input: 'protocol=https\nhost=github.com\n\n',
    encoding: 'utf8',
  });
  const m = out.match(/^password=(.*)$/m);
  if (!m || !m[1].trim()) {
    throw new Error('没能从凭据管理器取到 GitHub token（git credential fill 没返回 password）');
  }
  return m[1].trim();
}

/** 把 curl 的配置（含 token）从 stdin 喂进去；返回 { status, body } */
function curl(configLines) {
  const cfg = configLines.join('\n') + '\n';
  const out = execFileSync(CURL, ['-sS', '--config', '-', '-w', '\n%{http_code}'], {
    cwd: ROOT,
    input: cfg,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
  const nl = out.lastIndexOf('\n');
  if (nl < 0) throw new Error('curl 没返回 http 状态码：' + out);
  return { status: parseInt(out.slice(nl + 1).trim(), 10), body: out.slice(0, nl) };
}

function save(name, text) {
  fs.mkdirSync(ARCHIVE, { recursive: true });
  fs.writeFileSync(path.join(ARCHIVE, name), text);
}

function main() {
  const [zipArg, notesArg, tagArg, titleArg] = process.argv.slice(2);
  if (!zipArg || !notesArg) {
    console.log('用法：node .workbuddy/tools/release.js <zip> <notes.md> [tag] [release 标题]');
    process.exit(1);
  }
  const zipPath = path.resolve(ROOT, zipArg);
  const notesPath = path.resolve(ROOT, notesArg);
  if (!fs.existsSync(zipPath)) throw new Error('找不到安装包：' + zipPath);
  if (!fs.existsSync(notesPath)) throw new Error('找不到发布说明：' + notesPath);

  const zipName = path.basename(zipPath);
  const version = (zipName.match(/(\d+\.\d+\.\d+)/) || [, ''])[1];
  const tag = tagArg || 'v' + version;
  const title = titleArg || tag;
  const body = fs.readFileSync(notesPath, 'utf8');

  const token = readToken();
  const auth = [
    `header = "Authorization: Bearer ${token}"`,
    'header = "Accept: application/vnd.github+json"',
    'header = "X-GitHub-Api-Version: 2022-11-28"',
    'header = "User-Agent: thief-book-idea-pro-release"',
    `proxy = "${PROXY}"`,
  ];

  // ---- 1. 建 release ----
  const payload = { tag_name: tag, target_commitish: 'master', name: title, body, draft: false };
  const payloadFile = path.join(ARCHIVE, 'payload.json');
  fs.mkdirSync(ARCHIVE, { recursive: true });
  fs.writeFileSync(payloadFile, JSON.stringify(payload, null, 2));

  const created = curl([
    `url = "https://api.github.com/repos/${REPO}/releases"`,
    'request = "POST"',
    'header = "Content-Type: application/json"',
  ].concat(auth, [`data-binary = "@${curlPath(payloadFile)}"`]));
  save('release-resp.json', created.body);
  if (created.status !== 201) {
    throw new Error(`建 release 失败 HTTP ${created.status}\n${created.body}`);
  }
  const data = JSON.parse(created.body);
  save('release-id.txt', String(data.id));
  console.log(`release 已创建：${tag}  id=${data.id}`);
  console.log('  ' + data.html_url);

  // ---- 2. 上传安装包（走 uploads.github.com）----
  const uploadUrl = String(data.upload_url).replace(/\{.*$/, '') + '?name=' + encodeURIComponent(zipName);
  const uploaded = curl([
    `url = "${uploadUrl}"`,
    'request = "POST"',
    'header = "Content-Type: application/zip"',
  ].concat(auth, [`data-binary = "@${curlPath(zipPath)}"`]));
  save('asset-resp.json', uploaded.body);
  if (uploaded.status !== 201) {
    throw new Error(`上传安装包失败 HTTP ${uploaded.status}\n${uploaded.body}`);
  }
  const asset = JSON.parse(uploaded.body);
  console.log(`安装包已上传：${asset.name}  ${(asset.size / 1024).toFixed(0)} KB`);
  console.log('  ' + asset.browser_download_url);

  console.log('\n发布完成。发布页：' + data.html_url);
}

try {
  main();
} catch (e) {
  console.error('失败：' + e.message);
  process.exit(1);
}
