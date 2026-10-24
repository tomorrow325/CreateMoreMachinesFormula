# Design

## Context

「查看机器工作等级」现状只有工程师护目镜一条常驻通道（`GoggleLines.append` 唯一逻辑出口、`MachineRoster` 唯一名单、3 个 goggle mixin 按 Create 类层级分流——字节码已核对 6 台名单机全覆盖）。运行等级 Data Attachment 已带 sync handler 随区块同步到客户端（`MachinePriority.RUNNING_TIER`），客户端读方块实体即得，无需任何新数据通道。Jade（Snownee，WAILA 系后继）是 1.21.1 NeoForge 主流方块信息 HUD，插件 API 纯客户端：`IWailaPlugin` 入口 + `IBlockComponentProvider` 按方块类注册，Jade 自行实例化扫描到的插件类。

## Goals / Non-Goals

**Goals:**

- Jade 在场时，HUD 对 6 台名单机显示与护目镜逐字相同的两行（复用同一语言键与颜色规则），无需戴护目镜。
- 判定规则与护目镜**同源**：同一份 `MachineRoster`、同一套 `GoggleLines` 构造逻辑、同一套破碎轮「轮子 → 控制器」反查。
- Jade 缺席零影响：不加载任何 Jade 相关类、无报错（`runClientNoOpt` 场景可验证）。
- 遵循项目既有可选依赖模式：jar 入 `libs/`、compileOnly 无条件、runtimeOnly 受 `-PnoOptionalRuntime` 门控。

**Non-Goals:**

- 不动护目镜侧任何代码（3 个 goggle mixin、显示语义）。
- 不做「(手动)」标记、不给鼓风机等刻意排除项开例外（探索阶段已拍板）。
- 不做 Jade 的物品侧/实体侧提供器、不做主题定制；Jade 自带的 per-plugin 开关（用户可在 Jade 配置里禁用本插件行）即为唯一的用户级配置面，不另造配置。

## Decisions

### D1：插件注册用 `@WailaPlugin` 注解，不写 `jade_plugins.json`

Jade 15.x 官方参考 addon（JadeAddons）以注解注册；json 清单是历史机制。注解由 Jade 的扫描器在启动时发现并实例化插件类——**NeoForge 与本模组自身都不会加载该类**，缺席即天然门控，这正是选它而不是 mixin 的原因。备选（json 清单）被否：两套并存有重复注册歧义，单一机制更可审计。

### D2：提供器按 7 个具体方块类注册，直接 `import` 类常量

`registerBlockComponent(provider, X.class)` 的目标：6 台名单机的方块类 + 破碎轮「轮子」方块类（数据经 `CrushingWheelPair` 反查控制器）。CMM 是**必需依赖**，方块类直接 import（`CMMMechanicalPressBlock.class` 等），零反射、编译期即校验。备选（注册到 `Block.class` 基类、provider 内部过滤）被否：会让回调对所有方块每帧触发，虽然过滤只是一次 Set 查询，但按类注册更克制也更符合 Jade 插件礼仪。

### D3：两行内容抽 `GoggleLines.buildLines(be)` 共享出口

现状 `append(tooltip, be)` 把「构造两行」与「写入 tooltip」耦合。抽一个 `@Nullable List<Component> buildLines(BlockEntity)`（不受支持返回 null），`append` 与 Jade 提供器共同调用——护目镜行为逐字节不变，Jade 侧拿到同一份 `Component`（同一 translatable 键 `more_formula.goggle.machine_tier` / `running_tier`，同一 `TierNames.colored` 颜色）。

### D4：依赖与版本按项目惯例

Jade jar（1.21.1 NeoForge 15.x，从 Modrinth/CurseForge 下载）入 `libs/`；`gradle.properties` 加 `jade_version`；`build.gradle` 加 compileOnly 无条件 + runtimeOnly 门控段（与 Mekanical-Create 同款 fileTree/坐标写法）；mods.toml 加 `jade` optional 条目 `versionRange = "[${jade_version},)"`。`tools/genargs.js` 的 `JAR_DIRS` 按通配纳入 `libs/`，编译 classpath 自动覆盖，无需改动。

### D5：隔离约定——`compat/jade/` 包外零引用

Jade API 类只允许出现在 `compat/jade/` 包内；本模组主类、事件、任何 mixin 不得引用插件类，否则 Jade 缺席时会 NoClassDefFoundError。与既有 `compat/handmade`、`compat/mekanicalcreate` 的隔离纪律一致（那两个靠 MixinConfigPlugin，这里靠「Jade 是唯一实例化方」）。

## Risks / Trade-offs

- **Jade API 在 15.x 内有小幅签名变动**（`Tooltip#add` 语义、注册方法名）：以 sources jar 在实现期核对，属实现核对点而非设计风险；选定入 `libs/` 的版本即编译基准，下限声明同版本。
- **按方块类注册 vs CMM 换方块类**：CMM 升级若重命名方块类会编译期报错（必需依赖、直接 import 的好处——断裂发生在编译而非运行）。
- **两行常驻 HUD 占位**：Jade 用户若嫌长可在 Jade 配置里禁用本插件（免费的用户级开关）；本模组不另加开关，保持「主功能不用 TOML/配置文件」的项目约定。
