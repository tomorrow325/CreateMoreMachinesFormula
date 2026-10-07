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
// 坑：gradle 缓存里同一构件常同时有 binary / -sources / -javadoc 三个 hash 目录，
// readdirSync 的顺序不保证 binary 在前 —— 曾经挑到过 -javadoc.jar，
// javac 看不到任何类，报「程序包 xxx 不存在」。所以排除顺序必须把 javadoc 一起排掉。
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
  return found.find((f) => !f.endsWith('-sources.jar') && !f.endsWith('-javadoc.jar')) || found[0];
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

// Mixin 注解 API（org.spongepowered.asm.mixin.*，编译 @Mixin 类必需）。
// 坑：org.spongepowered:mixin:0.8.5 不在 Maven Central，本机 modules-2 缓存里
// 也没有（只有 NeoForge 21.1 运行时真正带的 Fabric fork
// net.fabricmc:sponge-mixin，包名与注解签名不变）。javac 只用注解，
// 两者兼容 —— 优先 0.8.5，缓存里没有时回退到 sponge-mixin。
function mixinJar() {
  try {
    return cached('org.spongepowered', 'mixin', '0.8.5');
  } catch {
    const dir = path.join(MODULE_CACHE, 'net.fabricmc', 'sponge-mixin');
    const versions = fs.existsSync(dir) ? fs.readdirSync(dir) : [];
    for (const v of versions) {
      try {
        const jar = cached('net.fabricmc', 'sponge-mixin', v);
        if (!jar.endsWith('-sources.jar')) return jar;
      } catch {
        // 该版本目录下没有 jar，试下一个版本
      }
    }
    throw new Error('missing mixin in module cache: org.spongepowered:mixin:0.8.5 与 net.fabricmc:sponge-mixin 均不存在');
  }
}

// MixinExtras 注解（@ModifyExpressionValue / @WrapOperation 等，
// mekanicalcreate 兼容 mixin 在用）。不在 genargs 的固定坐标清单里 ——
// 本机缓存里只有 io.github.llamalad7:mixinextras-neoforge（含完整注解类），
// 优先 common 构件，没有时回退 neoforge 变体。
function mixinExtrasJar() {
  const candidates = [
    ['io.github.llamalad7', 'mixinextras-common'],
    ['io.github.llamalad7', 'mixinextras-neoforge'],
  ];
  for (const [group, artifact] of candidates) {
    const dir = path.join(MODULE_CACHE, group, artifact);
    if (!fs.existsSync(dir)) continue;
    for (const v of fs.readdirSync(dir)) {
      try {
        return cached(group, artifact, v);
      } catch {
        // 该版本目录下没有可用的 jar，试下一个版本
      }
    }
  }
  throw new Error('missing MixinExtras in module cache: io.github.llamalad7:mixinextras-common / mixinextras-neoforge 均不存在');
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
  mixinJar(),
  mixinExtrasJar(),
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

// Maven 坐标的编译期依赖：Create / ponder / Registrate / flywheel API / JEI API /
// KubeJS / KubeJS-Create。以前它们随仓库放在 libs/（Create、KubeJS-Create 等），
// 或从 Create 的 jarJar 里就地解出（ponder / flywheel / Registrate）；现在全部走
// Gradle 坐标（见 build.gradle），手工链也从 modules-2 缓存取 —— 前提仍是这台机器
// 做过一次联网的 Gradle 构建（和 merged MC 包同一个前提）。
//
// 版本号不在这里写死：从 gradle.properties 与 build.gradle 解析，避免两边漂移。
function readProps(file) {
  const out = {};
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([-\w.]+)\s*=\s*(\S+)\s*$/);
    if (m) out[m[1]] = m[2];
  }
  return out;
}
const props = readProps(path.join(ROOT, 'gradle.properties'));
const buildGradleSrc = fs.readFileSync(path.join(ROOT, 'build.gradle'), 'utf8');

function coordinate(re, what) {
  const m = buildGradleSrc.match(re);
  if (!m) throw new Error('build.gradle 里找不到 ' + what + ' 的坐标');
  return m[1];
}

// rhino 是 kubejs 的 POM 传递依赖（版本随 kubejs 变化，且缓存里常同时留着新旧两版，
// 绝不能按目录名挑最高版）—— 优先从缓存里 kubejs 的 .pom 读出它钉死的 rhino 版本。
// 缓存里没有对应 POM 时（files-2.1 只留了 jar），回退到缓存里最新的 rhino：
// 这里只影响**编译期**（手工链不跑游戏），rhino 暴露给编译器的 API 在各版本间一致。
function rhinoForKubejs() {
  const dir = path.join(MODULE_CACHE, 'dev.latvian.mods', 'kubejs-neoforge', props.kubejs_version);
  let pom = null;
  if (fs.existsSync(dir)) {
    pom = (function walk(d) {
      for (const e of fs.readdirSync(d, { withFileTypes: true })) {
        const p = path.join(d, e.name);
        if (e.isDirectory()) {
          const hit = walk(p);
          if (hit) return hit;
        } else if (e.name.endsWith('.pom')) {
          return p;
        }
      }
      return null;
    })(dir);
  }
  if (pom) {
    const m = fs.readFileSync(pom, 'utf8')
      .match(/<artifactId>rhino<\/artifactId>\s*<version>([^<]+)<\/version>/);
    if (m) return m[1];
  }
  const rhinoRoot = path.join(MODULE_CACHE, 'dev.latvian.mods', 'rhino');
  const versions = fs.existsSync(rhinoRoot) ? fs.readdirSync(rhinoRoot) : [];
  if (!versions.length) throw new Error('缓存里找不到 kubejs 的 POM，也没有任何 rhino 构件: ' + dir);
  const newest = versions.sort((a, b) => a.localeCompare(b, undefined, { numeric: true })).pop();
  console.warn('[genargs] 警告: 缓存里没有 kubejs 的 POM，rhino 按缓存最高版编译: ' + newest
    + '（仅影响手工链编译，不影响 Gradle 运行时）');
  return newest;
}

const MAVEN_COMPILE_DEPS = [
  ['com.simibubi.create', 'create-1.21.1',
    coordinate(/com\.simibubi\.create:create-1\.21\.1:([\w.+-]+)/, 'Create')],
  ['net.createmod.ponder', 'ponder-neoforge',
    coordinate(/net\.createmod\.ponder:ponder-neoforge:([\w.+-]+)/, 'Ponder')],
  ['com.tterrag.registrate', 'Registrate',
    coordinate(/com\.tterrag\.registrate:Registrate:([\w.+-]+)/, 'Registrate')],
  ['dev.engine-room.flywheel', 'flywheel-neoforge-api-' + props.minecraft_version,
    coordinate(/flywheel-neoforge-api-\$\{minecraft_version\}:([\w.+-]+)/, 'Flywheel API')],
  ['mezz.jei', 'jei-' + props.minecraft_version + '-common-api', props.jei_version],
  ['mezz.jei', 'jei-' + props.minecraft_version + '-neoforge-api', props.jei_version],
  ['dev.latvian.mods', 'kubejs-neoforge', props.kubejs_version],
  ['dev.latvian.mods', 'rhino', rhinoForKubejs()],
  ['dev.latvian.mods', 'kubejs-create-neoforge',
    coordinate(/kubejs-create-neoforge:([\w.+-]+)/, 'KubeJS-Create')],
];
for (const [group, artifact, version] of MAVEN_COMPILE_DEPS) {
  CP.push(cached(group, artifact, version));
}

// 工程自带的 jar：libs/ 只剩无 maven 渠道的 5 个（CMM / Create: Hand Made /
// Mekanical-Create / Mekanism / CreateMoreMoreMachines），按通配纳入而不是写死文件名。
const JAR_DIRS = [
  { dir: path.join(ROOT, 'libs'), required: true, what: '无 maven 渠道的工程自带依赖' },
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
