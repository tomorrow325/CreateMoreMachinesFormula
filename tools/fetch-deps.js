// 准备 build/deps/：从 Create 的 jar 里解出 jarJar 打包的编译期依赖。
//
// 背景：Create 把 ponder / flywheel / Registrate 以 jarJar 形式内嵌在自己的 jar 里
// （META-INF/jarjar/*.jar）。编译本模组需要它们，但没必要把同一批 jar 再往仓库里存一份，
// 所以每次构建时就地解出来 —— 完全离线，不需要联网。
//
// 其余编译期依赖（KubeJS / rhino / JEI）已经直接放在仓库的 libs/ 里，
// 由 tools/genargs.js 一并纳入类路径，不经过本脚本。
//
// 用法：
//   node tools/fetch-deps.js           # 缺什么补什么
//   node tools/fetch-deps.js --force   # 全部重新解一遍
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { findTool, zipReadEntry, zipList } = require('./jdk');

const ROOT = path.resolve(__dirname, '..');
const DEPS = path.join(ROOT, 'build', 'deps');
const LIBS = path.join(ROOT, 'libs');
const FORCE = process.argv.includes('--force');

fs.mkdirSync(DEPS, { recursive: true });

/** 找一个 create 主 jar（形如 create-1.21.1-6.0.10.jar）。 */
function findCreateJar() {
  const hits = fs.readdirSync(LIBS).filter((f) => /^create[-.].*\.jar$/i.test(f));
  if (!hits.length) {
    throw new Error(
      'libs/ 下找不到 create 的 jar。\n' +
      '  libs/ 里的编译期依赖是随仓库一起分发的，缺了说明仓库不完整。'
    );
  }
  return path.join(LIBS, hits[0]);
}

/** 列出 create jar 里的 jarJar 条目。 */
function listJarJar(src) {
  const want = (l) => /^META-INF\/jarjar\/.+\.jar$/.test(l);
  // 优先用 JDK 的 jar；PATH 上可能没有 jar，所以带纯 Node 兜底
  try {
    const out = execFileSync(findTool('jar'), ['-tf', src], { encoding: 'utf8' });
    const names = out.split(/\r?\n/).map((s) => s.trim()).filter(want);
    if (names.length) return names;
  } catch {
    /* 落到纯 Node 分支 */
  }
  return zipList(src).filter(want);
}

/** 从 zip 里取出一个条目（优先 jar，失败则纯 Node 解压）。 */
function readEntry(src, entry) {
  const tmp = path.join(ROOT, 'build', 'jarjar-tmp');
  try {
    fs.rmSync(tmp, { recursive: true, force: true });
    fs.mkdirSync(tmp, { recursive: true });
    execFileSync(findTool('jar'), ['-xf', src, entry], { cwd: tmp, stdio: 'ignore' });
    const data = fs.readFileSync(path.join(tmp, entry));
    fs.rmSync(tmp, { recursive: true, force: true });
    return data;
  } catch {
    fs.rmSync(tmp, { recursive: true, force: true });
    const data = zipReadEntry(src, entry);
    if (!data) throw new Error('无法从 create jar 取出 ' + entry);
    return data;
  }
}

function main() {
  const src = findCreateJar();
  const entries = listJarJar(src);
  if (!entries.length) {
    throw new Error('create jar 里没有 META-INF/jarjar/ 条目：' + path.basename(src));
  }

  let added = 0;
  for (const entry of entries) {
    const name = path.basename(entry);
    const dst = path.join(DEPS, name);
    if (fs.existsSync(dst) && !FORCE) continue;
    fs.writeFileSync(dst, readEntry(src, entry));
    console.log('  [jarJar] ' + name);
    added++;
  }

  const total = fs.readdirSync(DEPS).filter((f) => f.endsWith('.jar')).length;
  console.log(added
    ? `完成：新增 ${added} 个，build/deps/ 共 ${total} 个。`
    : `build/deps/ 已就绪（${total} 个），无需变动。`);
}

main();
