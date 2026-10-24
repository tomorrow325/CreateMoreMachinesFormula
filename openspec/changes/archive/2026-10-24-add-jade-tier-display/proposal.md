# Proposal

## Why

「查看机器工作等级」目前唯一的常驻通道是工程师护目镜 tooltip：没戴护目镜的玩家想确认某台机器的当前档位，只能用扳手调一下看动作栏——把机器改了才能看，体验不合理（探索阶段已确认护目镜对 6 台名单机的覆盖无缺口，问题纯在通道单一）。Jade 是 1.21.1 NeoForge 生态的主流方块信息 HUD（WAILA 系后继），为它提供同源集成即可零装备查看，且插件 API 纯客户端、Jade 缺席时插件类不加载，天然适配本项目「可选依赖只在 runtime 层门控」的既有模式。

## What Changes

- **新增 Jade 可选兼容层** `compat/jade/`：`IWailaPlugin` 入口（`@WailaPlugin` 注解 + `assets/more_formula/jade_plugins.json` 双保险注册）+ `IBlockComponentProvider` 方块提供器，在 Jade 顶部 HUD 为受支持机器追加与护目镜完全相同的两行（「机械等级」「运行等级」）。
- **显示规则与护目镜同源同规则**：复用 `GoggleLines`/`MachineRoster`/`CrushingWheelPair`（逻辑与名单零改动）；6 台名单机显示，名单外（含分级鼓风机、盆、置物台、流体罐、蒸汽引擎、原版机器）不显示；破碎轮看「轮子」反查「控制器」；半结构反查不到则不显示。**不需要戴护目镜**——这正是本变更的价值。
- **数据通道零新增**：运行等级 Data Attachment 已随区块同步到客户端（`MachinePriority` 的 sync handler），Jade 提供器在客户端直接读方块实体，无 `IServerDataProvider`、无 mixin。
- **依赖与门控**：Jade jar 入 `libs/`（与 Create: Hand Made / Mekanical-Create 同款：compileOnly 无条件、runtimeOnly 受 `-PnoOptionalRuntime` 门控）；mods.toml 增加 `jade` optional 条目；无独立 mixins.json、无 MixinConfigPlugin。
- **明确不做**（探索阶段已拍板）：「(手动)」标记（手动调回出厂同档时与未调过显示一致，维持现状）；鼓风机等刻意排除项的任何例外；工程师护目镜侧的任何改动（3 个 goggle mixin 不动）。

## Capabilities

### New Capabilities

（无 —— JADE 显示是 `run-tier` 既有能力的新显示面。）

### Modified Capabilities

- `run-tier`: 新增「Jade HUD 显示」需求——Jade 在场时对名单内机器显示与护目镜同源同规则的两行等级信息，无需护目镜；Jade 缺席时行为不变。既有「护目镜两行显示」需求不动。

## Impact

- **代码**：新增 `src/main/java/org/minecart/more_formula/compat/jade/`（插件入口 + 提供器，预计 2 个类）；`GoggleLines` 可能需抽一个「构造两行 Component」的共享出口供 Jade 复用（不改变护目镜行为）。
- **依赖**：`libs/` 新增 Jade 1.21.1 NeoForge 15.x jar（Modrinth/CurseForge 渠道）；`build.gradle` 增加可选依赖段；`gradle.properties` 增加 `jade_version`；mods.toml 模板增加 optional 条目。
- **资源**：`assets/more_formula/jade_plugins.json`（插件登记）。
- **验证**：`runClient`（Jade 在场）冒烟确认两行显示且名单外不显示；`runClientNoOpt`（Jade 缺席）确认零报错；离线回归不受影响（纯客户端类不进 ConfigTest）。
