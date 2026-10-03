// 定位 JDK 里的工具（java / javac / jar），并提供一个纯 Node 的 zip 解压兜底。
//
// 为什么需要它：Windows 的 PATH 上常见 Oracle javapath 转发器，**不含 jar**。
// 所以不能假设 `jar` 一定可执行 —— 否则 clone 下来第一步就 ENOENT。
//
// 查找顺序：
//   1) JAVA_HOME/bin/<tool>
//   2) JAVA_HOME（去掉 JRE 后缀再试）
//   3) 环境变量 JDK_HOME / JAVA_21_HOME
//   4) 常见安装位置（Program Files\Java\* / Program Files\Eclipse Adoptium\* 等）
//   5) PATH（交给系统）
//
// 另外 zips 的解压有一个不依赖外部命令的实现（zipReadEntry），
// 用于在完全没有 jar 的情况下仍能从 create jar 里取出 jarJar 依赖。
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

function candidates(name) {
  const exe = process.platform === 'win32' ? name + '.exe' : name;
  const out = [];
  const push = (dir) => {
    if (!dir) return;
    out.push(path.join(dir, 'bin', exe));
  };
  push(process.env.JAVA_HOME);
  push(process.env.JDK_HOME);
  push(process.env.JAVA_21_HOME);

  // 常见安装根目录
  const roots = [];
  if (process.platform === 'win32') {
    roots.push('C:\\Program Files\\Java', 'C:\\Program Files\\Eclipse Adoptium',
      'C:\\Program Files\\Microsoft\\jdk', 'C:\\Program Files\\Amazon Corretto',
      'C:\\Program Files\\Zulu', 'C:\\Program Files\\BellSoft\\LibericaJDK');
  } else {
    roots.push('/usr/lib/jvm', '/Library/Java/JavaVirtualMachines');
  }
  for (const root of roots) {
    let entries = [];
    try { entries = fs.readdirSync(root); } catch { continue; }
    for (const e of entries) out.push(path.join(root, e, 'bin', exe));
  }
  out.push(exe); // 最后交给 PATH
  return out;
}

/** 返回可执行工具的路径；全都找不到时返回名字本身（当作 PATH 查找）。 */
function findTool(name) {
  for (const c of candidates(name)) {
    if (c === name) continue;
    try { if (fs.existsSync(c)) return c; } catch { /* ignore */ }
  }
  return name;
}

// ---------------------------------------------------------------------------
// 纯 Node 的 zip 条目读取（兜底用，避免依赖 jar/unzip）
// 支持 stored(0) 与 deflate(8)，够用 —— jarJar 里的 jar 就是普通 deflate。
// ---------------------------------------------------------------------------
function zipReadEntry(zipPath, entryName) {
  const zlib = require('zlib');
  const buf = fs.readFileSync(zipPath);

  // 从尾部找 End of Central Directory (EOCD, 0x06054b50)
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 66000); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('not a zip (no EOCD): ' + zipPath);

  const cdOffset = buf.readUInt32LE(eocd + 16);
  const cdCount = buf.readUInt16LE(eocd + 10);

  let p = cdOffset;
  for (let i = 0; i < cdCount; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) break;
    const method = buf.readUInt16LE(p + 10);
    const compSize = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const localOffset = buf.readUInt32LE(p + 42);
    const name = buf.slice(p + 46, p + 46 + nameLen).toString('utf8');

    if (name === entryName) {
      // 读 local header 拿真实数据起始位置
      if (buf.readUInt32LE(localOffset) !== 0x04034b50) throw new Error('bad local header');
      const lNameLen = buf.readUInt16LE(localOffset + 26);
      const lExtraLen = buf.readUInt16LE(localOffset + 28);
      const dataStart = localOffset + 30 + lNameLen + lExtraLen;
      const comp = buf.slice(dataStart, dataStart + compSize);
      if (method === 0) return comp;
      if (method === 8) return zlib.inflateRawSync(comp);
      throw new Error('unsupported zip compression method ' + method);
    }
    p += 46 + nameLen + extraLen + commentLen;
  }
  return null;
}

/** 列出 zip 里的全部条目名（纯 Node）。 */
function zipList(zipPath) {
  const buf = fs.readFileSync(zipPath);
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 66000); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('not a zip (no EOCD): ' + zipPath);
  const cdOffset = buf.readUInt32LE(eocd + 16);
  const cdCount = buf.readUInt16LE(eocd + 10);
  const names = [];
  let p = cdOffset;
  for (let i = 0; i < cdCount; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) break;
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    names.push(buf.slice(p + 46, p + 46 + nameLen).toString('utf8'));
    p += 46 + nameLen + extraLen + commentLen;
  }
  return names;
}

module.exports = { findTool, zipReadEntry, zipList };
