// 准备 build/deps/：从 Create 的 jar 里解出 jarJar 打包的编译期依赖。
//
// 【已过时但保留兼容】编译期依赖换成 Gradle maven 坐标后（Create 6.0.9+ 本体也走坐标），
// ponder / flywheel / Registrate 由 tools/genargs.js 直接从 modules-2 缓存取，
// 不再需要本脚本解包。libs/ 里已没有 create 的 jar —— 脚本检测到这一点就直接跳过；
// 若 libs/ 里仍有 create jar（旧检出），行为保持原样。
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

/** 找一个 create 主 jar（旧检出里形如 create-1.21.1-6.0.10.jar）；没有则返回 null（坐标时代属正常）。 */
function findCreateJar() {
  const hits = fs.readdirSync(LIBS).filter((f) => /^create[-.].*\.jar$/i.test(f) && !f.startsWith('create_hand_made'));
  if (!hits.length) {
    console.log('libs/ 下没有 create 的 jar（编译期依赖已走 Gradle 坐标），跳过解包。');
    return null;
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
  if (!src) return;
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
