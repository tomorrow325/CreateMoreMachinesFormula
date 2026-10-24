# AGENTS.md

NeoForge 1.21.1 Minecraft 模组 **CreateMoreMachinesFormula**（`mod_id = more_formula`，Java 21，Gradle 8.8 + ModDevGradle）。为 Create: More Machines (CMM) 的高级机器增加配方等级门槛（Tier.ZERO/BRASS/NETHERITE/END/BEYOND/CREATIVE，CREATIVE=-1 为特殊语义），门槛全部通过 KubeJS 配置。

**给 agent 的沟通约定：本项目文档、注释、提交信息均使用中文（zh-CN）。**

## 构建与测试（Windows，详细以 BUILD.md 为准）

```bash
gradlew.bat build                                  # 标准构建，产物 build/libs/CreateMoreMachinesFormula-<版本>.jar
gradlew.bat check                                  # 构建 + ConfigTest 回归（114 条断言）
gradlew.bat runClient                              # 全依赖客户端（run/，JEI + 4 个可选兼容 mod 全在场）
gradlew.bat -PnoOptionalRuntime runClientNoOpt     # 无可选依赖客户端（run-noopt/，验证宿主 mod 缺席时不报错）
```

- 无网络兜底链：`build_run.cmd`（→ `node tools/build.js`），依赖 Gradle 缓存里已解好的合并包；四个 javac 坑（argfile 反斜杠、非 ASCII 路径、PowerShell UTF-16LE 重定向、多个 outputs.jar）已固化在 `tools/*.js`，不要绕过该链手写 javac。
- **手工跑测试必须两步连跑**：先 `node tools/build.js` 再 `node tools/run_tests.js`。单跑 `run_tests.js` 会撞上 `build/classes` 里 Gradle 与手工链的混合陈旧产物（典型症状：`ConfigTest` 报 `isMinimumTier 找不到符号`）。

## 目录结构

- `src/main/java/org/minecart/more_formula/` — `Config.java`（门槛表，核心状态）、`DeployerRecipeGate.java`、`More_formula.java`（主类）
  - `mixin/` — 核心 Create/CMM mixin（对应 `more_formula.mixins.json`）
  - `compat/kubejs/` — KubeJS 脚本 API（`Tier` 常量、`MoreFormula` 绑定、事件）
  - `compat/handmade/`、`compat/mekanicalcreate/`、`compat/createmoremoremachines/` — 三个可选 mod 的兼容，各有独立 `more_formula-*.mixins.json` + `*MixinConfigPlugin`
  - `compat/jei/` — 分级 JEI 分类与动画
- `src/main/templates/META-INF/neoforge.mods.toml` — 带 `${}` 占位符的模板，版本号来自 `gradle.properties`（`generateModMetadata` 展开）
- `src/test/java/.../ConfigTest.java` — main() 式离线回归（非 JUnit），只依赖 `ResourceLocation`，不需游戏运行时
- `libs/` — 5 个无 maven 渠道、随仓库分发的 jar（CMM 2.7、Hand Made、Mekanical-Create、Mekanism、CMMM）
- `tools/*.js` — 手工构建/测试链；`docs/` — KubeJS 文档；`openspec/` — 规格驱动变更流程

## 架构与编辑规则

- **`mod_id` 永远是 `more_formula`，不可改名**；显示名/JAR 名是 `CreateMoreMachinesFormula`。命名空间、KubeJS 脚本 API、配置都以 mod_id 为准。
- **可选依赖只在 runtime 层门控**：compileOnly 永远无条件（两种客户端共用同一套编译产物）；运行时通过各自的 `MixinConfigPlugin.shouldApplyMixin`（mod 缺席时恒 false）与 `-PnoOptionalRuntime` 挡在 classpath 外。**KubeJS / KubeJS-Create 绝不门控**——mods.toml 里是 required，排除会导致拒绝加载。
- 为某个可选 mod 新增 mixin 时：放进对应 `compat/<mod>/mixin/` 包，并登记到该 mod 自己的 `more_formula-*.mixins.json`，不要混入核心 `more_formula.mixins.json`。
- 依赖版本下限是实测过的（Create 6.0.10 为有效下限、kubejs b.285 为有效下限），理由写在 `build.gradle` 注释里；不要随意升降版本。
- 门槛表（`Config`）按来源分桶：服务器脚本条目在每次 `/reload` 前清空、启动脚本条目保留；优先级：精确 > 前缀，同类下服务器脚本 > 启动脚本，前缀取最长匹配。改 `Config` 逻辑必须同步补 `ConfigTest` 断言。
- 未安装 CMMM 时不要给 crushing/milling/cutting 类型设门槛（无对应机器，配方会失效）；分级破碎轮不满足门槛时物品被销毁（原生行为），锯/磨石则原样退回。

## 改动前必读

- 构建系统、依赖来源、兜底链：`BUILD.md`
- KubeJS API 行为的权威说明：`docs/more_formula-KubeJS编写指南.md`（改脚本 API 前先读）
- 规格驱动变更走 `openspec/`（配套 openspec-propose / apply-change / archive-change 等 skill），产物用中文撰写，保留 SHALL/MUST 英文关键字

## 其他约定

- 行尾统一 LF（`.gitattributes` 强制；`.bat`/`.cmd` 保持 CRLF），保证跨机构建产物逐字节一致。
- 主功能不使用 TOML 配置文件；唯一例外是 Mekanical-Create 的速度/并行倍率（服务器 TOML 可调）。
