// 生成 javac 的 @argfile。
//
// 为什么不用 Gradle：当 Maven 仓库（maven.neoforged.net 等）不可达、ModDevGradle 无法解析时，
// 可以复用 Gradle 缓存里已经解好的 MC+NeoForge 合并包（ng_execute/outputs.jar）手工 javac。
//
// 两个必须遵守的约束（都踩过）：
//  1) javac 对 argfile 里的反斜杠做转义 —— 所有路径统一转成正斜杠；
//  2) argfile 按平台默认编码读取 —— 因此依赖一律先拷到纯 ASCII 路径（build/deps/）。
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const toSlash = (p) => p.replace(/\\/g, '/');

// Gradle 缓存根目录。默认用当前用户的 ~/.gradle，
// 可用环境变量 GRADLE_USER_HOME 覆盖 —— 不要把某个人的绝对路径写死进仓库。
const GRADLE_HOME = process.env.GRADLE_USER_HOME
  || path.join(process.env.USERPROFILE || process.env.HOME || '', '.gradle');
const MODULE_CACHE = toSlash(path.join(GRADLE_HOME, 'caches', 'modules-2', 'files-2.1'));

// 在模块缓存里找一个构件；同版本多个 hash 目录时取第一个。
function cached(group, artifact, version) {
  const dir = path.join(MODULE_CACHE, group, artifact, version);
  if (!fs.existsSync(dir)) throw new Error('missing in module cache: ' + group + ':' + artifact + ':' + version);
  const found = [];
  (function walk(d) {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith('.jar')) found.push(toSlash(p));
    }
  })(dir);
  if (!found.length) throw new Error('no jar in ' + dir);
  return found.find((f) => !f.endsWith('-sources.jar')) || found[0];
}

// 已解好的 Minecraft + NeoForge 合并包（neoforge 21.1.248 / MC 1.21.1）。
// 若缓存被清空，需要先跑一次联网的 Gradle 构建来重建。
const NG_EXECUTE = toSlash(path.join(GRADLE_HOME, 'caches', 'ng_execute'));

// 已知可用的那份 merged 包（NeoForge 21.1.248 / MC 1.21.1）：
// 用它的哈希目录名固定下来，避免在多个候选间挑错版本。
// 编译结果已与官方 0.0.1 成品 jar 逐条比对过。
const KNOWN_GOOD_MERGED_HASH = '2c1b0a4d97c2d36f4365c59c41230d7b2e484a0466d9053c0887526ce30a9eb3';

function mergedMinecraft() {
  // ng_execute 下可能有多个 outputs.jar（对应不同 NeoForge 版本）。
  // 只按体积挑是不确定的，所以先认那个已验证的哈希目录；它没了再回退挑选。
  const pinned = path.join(NG_EXECUTE, KNOWN_GOOD_MERGED_HASH, 'outputs.jar');
  if (fs.existsSync(pinned)) {
    return toSlash(pinned);
  }
  const cands = [];
  for (const d of fs.readdirSync(NG_EXECUTE)) {
    const jar = path.join(NG_EXECUTE, d, 'outputs.jar');
    if (fs.existsSync(jar)) cands.push({ jar: toSlash(jar), size: fs.statSync(jar).size });
  }
  // 合并包体积 > 18MB 才可能是完整的 MC+NeoForge（其余都是零散产物）
  const big = cands.filter((c) => c.size > 18 * 1024 * 1024).sort((a, b) => b.size - a.size);
  if (!big.length) throw new Error('no merged Minecraft jar in ng_execute (need one online Gradle build first)');
  return big[0].jar;
}

const CP = [
  mergedMinecraft(),
  cached('com.mojang', 'authlib', '6.0.54'),
  cached('com.mojang', 'blocklist', '1.0.10'),
  cached('com.mojang', 'brigadier', '1.3.10'),
  cached('com.mojang', 'datafixerupper', '8.0.16'),
  cached('com.mojang', 'logging', '1.2.7'),
  cached('com.mojang', 'patchy', '2.2.10'),
  cached('com.mojang', 'text2speech', '1.17.9'),
  cached('org.slf4j', 'slf4j-api', '2.0.9'),
  cached('it.unimi.dsi', 'fastutil', '8.5.12'),
  cached('net.neoforged', 'accesstransformers', '13.0.1'),
  cached('net.neoforged', 'bus', '8.0.5'),
  cached('net.neoforged', 'coremods', '7.0.3'),
  cached('net.neoforged', 'JarJarFileSystems', '0.4.1'),
  cached('net.neoforged', 'JarJarMetadata', '0.4.1'),
  cached('net.neoforged', 'JarJarSelector', '0.4.1'),
  cached('net.neoforged', 'srgutils', '1.0.0'),
  cached('net.neoforged.fancymodloader', 'loader', '4.0.42'),
  cached('net.neoforged.fancymodloader', 'earlydisplay', '4.0.42'),
  cached('org.spongepowered', 'mixin', '0.8.5'),
  cached('org.ow2.asm', 'asm', '9.10.1'),
  cached('org.ow2.asm', 'asm-tree', '9.10.1'),
  cached('org.ow2.asm', 'asm-analysis', '9.10.1'),
  cached('org.ow2.asm', 'asm-commons', '9.10.1'),
  cached('org.ow2.asm', 'asm-util', '9.10.1'),
  cached('com.google.code.gson', 'gson', '2.10.1'),
  cached('com.google.guava', 'guava', '32.1.2-jre'),
  cached('com.google.guava', 'failureaccess', '1.0.1'),
  cached('org.apache.commons', 'commons-lang3', '3.14.0'),
  cached('org.apache.logging.log4j', 'log4j-api', '2.22.1'),
  cached('org.apache.logging.log4j', 'log4j-core', '2.22.1'),
  cached('org.apache.logging.log4j', 'log4j-slf4j2-impl', '2.22.1'),
  cached('org.jetbrains', 'annotations', '24.1.0'),
  cached('org.joml', 'joml', '1.10.5'),
];

// 工程自带的 jar：libs/ 是**进仓库**的编译期依赖（Create / CMM / KubeJS-Create /
// KubeJS / rhino / JEI），build/deps/ 是从 Create 的 jarJar 里解出来的
// （ponder / flywheel / Registrate，不进仓库，由 tools/fetch-deps.js 就地生成）。
//
// 两个目录都按通配纳入，而不是写死文件名 —— 因为 KubeJS / JEI 的可用版本可能因机器而异
// （build.gradle 声明一套、整合包可能装了更新的一套），只要目录里有对应构件就能编译。
const JAR_DIRS = [
  { dir: path.join(ROOT, 'libs'), required: true, what: '工程自带的编译期依赖' },
  { dir: path.join(ROOT, 'build', 'deps'), required: false, what: 'jarJar 解出的依赖（fetch-deps 生成）' },
];

for (const { dir, required, what } of JAR_DIRS) {
  const jars = fs.existsSync(dir)
    ? fs.readdirSync(dir).sort().filter((f) => f.endsWith('.jar') && !/-sources\.jar$|-javadoc\.jar$/.test(f))
    : [];
  if (required && jars.length === 0) {
    throw new Error(`${dir} 里没有任何 jar（${what}）—— 仓库不完整？`);
  }
  for (const f of jars) CP.push(toSlash(path.join(dir, f)));
}

for (const p of CP) {
  if (!fs.existsSync(p)) throw new Error('classpath entry does not exist: ' + p);
}

const srcDir = path.join(ROOT, 'src', 'main', 'java');
const sources = [];
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.java')) sources.push(toSlash(p));
  }
})(srcDir);
sources.sort();

const out = path.join(ROOT, 'build', 'classes');
fs.mkdirSync(out, { recursive: true });

const args = [
  '-encoding', 'UTF-8',
  '-proc:none',
  '-Xmaxerrs', '2000',
  '-Xmaxwarns', '200',
  '-nowarn',
  '-d', '"' + toSlash(out) + '"',
  '-cp', '"' + CP.join(';') + '"',
  ...sources.map((s) => '"' + s + '"'),
];

process.stdout.write(args.join('\n') + '\n');
