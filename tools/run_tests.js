// Config 回归测试的运行器。
//
// 为什么要单独一个脚本：跑这个测试需要「编译期精选类路径 + MC 运行时依赖」
// 叠加，而且 -Dfile.encoding 在 PowerShell 里会被吃掉（必须走 JAVA_TOOL_OPTIONS），
// 否则中文断言名会变乱码。把这些坑固化在这里，避免下次重新踩。
const fs = require('fs');
const path = require('path');
const { execFileSync, spawnSync } = require('child_process');
const { findTool } = require('./jdk');

const ROOT = path.resolve(__dirname, '..');
const JAVA = findTool('java');
const JAVAC = findTool('javac');
// Gradle 缓存根：默认 ~/.gradle，可用 GRADLE_USER_HOME 覆盖。
const GRADLE_HOME = process.env.GRADLE_USER_HOME
  || path.join(process.env.USERPROFILE || process.env.HOME || '', '.gradle');
const MODULE_CACHE = path.join(GRADLE_HOME, 'caches', 'modules-2', 'files-2.1');

// 复用 genargs.js 的精选类路径（版本已对齐），避免手工再拼一遍。
// 注意不要用 shell 的 `>` 重定向去接 genargs 的输出：Windows PowerShell 的
// `>` 写的是 UTF-16LE，javac 读 @argfile 时会看到乱码。这里直接在进程内取 stdout。
const genargs = spawnSync(process.execPath, [path.join(__dirname, 'genargs.js')], { encoding: 'utf8' });
if (genargs.status !== 0) {
  console.error(genargs.stderr || 'genargs.js failed');
  process.exit(1);
}
const argsLines = genargs.stdout.split(/\r?\n/);
const cpIdx = argsLines.indexOf('-cp');
const curated = argsLines[cpIdx + 1].replace(/^"|"$/g, '');

// 补上 MC 运行时真正需要、但编译期不需要的模块（netty、modlauncher、securejarhandler 等）。
// 这些在 modules-2 缓存里都有，按坐标取即可 —— 不依赖任何其它工程的产物。
const RUNTIME_EXTRA = [
  ['cpw.mods', 'modlauncher', '11.0.5'],
  ['cpw.mods', 'securejarhandler', '3.0.8'],
  ['cpw.mods', 'bootstraplauncher', '2.0.2'],
  ['io.netty', 'netty-buffer', '4.1.97.Final'],
  ['io.netty', 'netty-codec', '4.1.97.Final'],
  ['io.netty', 'netty-common', '4.1.97.Final'],
  ['io.netty', 'netty-handler', '4.1.97.Final'],
  ['io.netty', 'netty-resolver', '4.1.97.Final'],
  ['io.netty', 'netty-transport', '4.1.97.Final'],
  ['io.netty', 'netty-transport-native-unix-common', '4.1.97.Final'],
  // Config / MekanicalCreateSpeedConfig 的 TOML 读取走 night-config；
  // 新增的测试一加载它们就需要这两个模块，否则 NoClassDefFoundError。
  ['com.electronwill.night-config', 'core', '3.8.3'],
  ['com.electronwill.night-config', 'toml', '3.8.3'],
];

function cached(group, artifact, version) {
  const dir = path.join(MODULE_CACHE, group, artifact, version);
  const found = [];
  (function walk(d) {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith('.jar') && !e.name.endsWith('-sources.jar') && !e.name.endsWith('-javadoc.jar')) found.push(p);
    }
  })(dir);
  if (!found.length) throw new Error('missing ' + group + ':' + artifact + ':' + version);
  return found[0].replace(/\\/g, '/');
}

const extra = RUNTIME_EXTRA.map(([g, a, v]) => cached(g, a, v));

const testClasses = path.join(ROOT, 'build', 'test-classes');
fs.rmSync(testClasses, { recursive: true, force: true });
fs.mkdirSync(testClasses, { recursive: true });

const testSrc = path.join(ROOT, 'src', 'test', 'java');
const compileCp = [path.join(ROOT, 'build', 'classes').replace(/\\/g, '/'), curated].join(';');
const sources = [];
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.java')) sources.push(p);
  }
})(testSrc);

execFileSync(JAVAC, ['-encoding', 'UTF-8', '-proc:none', '-nowarn', '-d', testClasses, '-cp', compileCp, ...sources], { stdio: 'inherit' });

const runCp = [testClasses.replace(/\\/g, '/'), path.join(ROOT, 'build', 'classes').replace(/\\/g, '/'), curated, ...extra].join(';');

// -Dfile.encoding 必须走 JAVA_TOOL_OPTIONS：PowerShell 会把它拆坏。
const env = Object.assign({}, process.env, { JAVA_TOOL_OPTIONS: '-Dfile.encoding=UTF-8' });
const run = spawnSync(JAVA, ['-cp', runCp, 'org.minecart.more_formula.ConfigTest'], { env, encoding: 'utf8' });
const out = (run.stdout || '') + (run.stderr || '');
// JAVA_TOOL_OPTIONS 的那行提示没有价值，过滤掉
const lines = out.split(/\r?\n/).filter((l) => l.trim() && !l.startsWith('Picked up JAVA_TOOL_OPTIONS'));
const summary = lines.filter((l) => l.startsWith('PASSED=') || l.startsWith('FAIL'));
for (const l of summary) console.log(l);
process.exit(run.status === 0 ? 0 : 1);
