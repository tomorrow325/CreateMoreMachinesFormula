# Proposal

## Why

项目已发布 0.1.0，核心行为（门槛表、机器闸门、KubeJS API、JEI 分级展示、三个可选模组兼容）已经稳定并有 114 条离线回归断言覆盖，但 `openspec/specs/` 仍为空。没有规格基线，后续任何变更都无法对照「系统应当表现为什么行为」来验证，规格驱动流程也无从起步。本变更把已实现的行为固化为规格基线，为后续所有变更提供对照物。

## What Changes

- 新增 7 个能力规格，完整覆盖现有 0.1.0 的对外可观察行为（详见 Capabilities）。
- 纯文档化变更：不修改任何产品代码、脚本 API、依赖或构建行为，无 **BREAKING** 变更。
- 规格的事实来源是当前 `main`（b70b6e0）的实际实现、`ConfigTest` 回归断言与 README/BUILD 文档；规格只记录行为，不记录实现细节。

## Capabilities

### New Capabilities

- `tier-gating`: 门槛数据模型与判定规则 —— 等级常量语义（0/1..4/-1 创造级）、精确与前缀条目、按来源分桶（SERVER/STARTUP）的存取与热重载清理、优先级规则（精确 > 前缀、SERVER > STARTUP、最长前缀优先）、门槛判定纯函数语义。
- `machine-gates`: 机器侧配方闸门 —— 对辊压/搅拌/压实/注液/机械手/拿物装配/序列装配七类配方在 Create 与 CMM 机器上的拦截行为，含机器等级识别（原版 Create 机器视为 0 级、CMM 等级换算）、浇注候选取最严门槛、机械手搜索事件取消。
- `kubejs-api`: KubeJS 脚本配置面 —— `Tier`/`MoreFormula` 全局绑定、配方 `.tier()` 修饰、`MoreFormulaEvents.registerTier` 启动事件、服务器脚本重载前的 SERVER 桶清理时机。
- `jei-integration`: JEI 展示面 —— 分级分类按等级全集构建、运行时按当前门槛表隐藏/取消隐藏分类与补交配方、已设门槛配方从原版 Create 分类隐藏、催化剂按「满足门槛的各档机器」展示、CMMM 在场时的破碎/研磨/锯切分级分类。
- `compat/hand-made`: 可选依赖 Create: Hand Made —— 手工工具只允许处理未设门槛（Tier.ZERO）的配方，空值输入按拒绝处理；模组缺席时完全不生效且不影响启动。
- `compat/mekanicalcreate`: 可选依赖 Mekanical-Create —— CMM/CMMM 分级模块为 Mekanical-Create 机器提供门槛判定、速度/并行倍率（服务器 TOML 可调）与 JEI 按最低适用机器档位过滤；模组缺席时完全不生效。
- `compat/createmoremoremachines`: 可选依赖 CreateMoreMoreMachines —— 分级破碎轮/磨石/机械锯纳入等级门槛（破碎轮不满足时销毁、锯与磨石原样退回），及其 JEI 分级分类与序列装配分级锯渲染；模组缺席时完全不生效。

### Modified Capabilities

（无 —— `openspec/specs/` 当前为空，不存在被修改的能力。）

## Impact

- 仅新增 `openspec/changes/document-existing-behavior/specs/` 下的规格文件；不触及 `src/`、`build.gradle`、`libs/` 与运行时行为。
- 归档后这 7 份规格成为基线，后续功能性变更必须以 delta 形式引用并修改它们。
