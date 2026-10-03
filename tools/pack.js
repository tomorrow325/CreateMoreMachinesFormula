// 打包 more_formula：classes + resources + 展开占位符后的 neoforge.mods.toml。
//
// 对应 build.gradle 里的三件事：
//   1) ProcessResources 展开 src/main/templates/META-INF/neoforge.mods.toml 的 ${} 占位符；
//   2) sourceSets.main.resources 纳入 src/main/resources；
//   3) jar 任务把两者与编译产物一起打包。
// 这是 Gradle 不可用时的离线等价流程，手工复刻同样的结果。
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { findTool } = require('./jdk');

const ROOT = path.resolve(__dirname, '..');
const JAR = findTool('jar');

// ---- 读取 gradle.properties ----
const props = {};
for (const line of fs.readFileSync(path.join(ROOT, 'gradle.properties'), 'utf8').split(/\r?\n/)) {
  const t = line.trim();
  if (!t || t.startsWith('#')) continue;
  const eq = t.indexOf('=');
  if (eq < 0) continue;
  props[t.slice(0, eq).trim()] = t.slice(eq + 1).trim();
}

const modVersion = props.mod_version;
if (!modVersion) throw new Error('mod_version missing from gradle.properties');

// ---- 组装 stage 目录 ----
const stage = path.join(ROOT, 'build', 'stage');
fs.rmSync(stage, { recursive: true, force: true });
fs.mkdirSync(stage, { recursive: true });

const copyDir = (from, to) => {
  if (!fs.existsSync(from)) return;
  fs.mkdirSync(to, { recursive: true });
  for (const e of fs.readdirSync(from, { withFileTypes: true })) {
    const s = path.join(from, e.name);
    const d = path.join(to, e.name);
    if (e.isDirectory()) copyDir(s, d);
    else fs.copyFileSync(s, d);
  }
};

// 编译产物
copyDir(path.join(ROOT, 'build', 'classes'), stage);
// 静态资源
copyDir(path.join(ROOT, 'src', 'main', 'resources'), stage);

// ---- 展开 neoforge.mods.toml ----
const tplDir = path.join(ROOT, 'src', 'main', 'templates');
let toml = fs.readFileSync(path.join(tplDir, 'META-INF', 'neoforge.mods.toml'), 'utf8');
toml = toml.replace(/\$\{([^}]+)\}/g, (m, key) => {
  if (!(key in props)) throw new Error('unknown placeholder in neoforge.mods.toml: ' + key);
  return props[key];
});
fs.mkdirSync(path.join(stage, 'META-INF'), { recursive: true });
fs.writeFileSync(path.join(stage, 'META-INF', 'neoforge.mods.toml'), toml, 'utf8');

// ---- jar ----
const outDir = path.join(ROOT, 'build', 'libs');
fs.mkdirSync(outDir, { recursive: true });
const outJar = path.join(outDir, `${props.mod_id}-${modVersion}.jar`);
fs.rmSync(outJar, { force: true });

execFileSync(JAR, ['--create', '--file', outJar, '-C', stage, '.'], { stdio: 'inherit' });

const size = fs.statSync(outJar).size;
process.stdout.write(`PACKED ${outJar} (${size} bytes, version ${modVersion})\n`);
