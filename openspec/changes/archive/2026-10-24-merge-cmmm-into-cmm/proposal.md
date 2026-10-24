# Proposal

## Why

上游 CreateMoreMoreMachines（CMMM）已并入 Create: More Machines 主模组并发布合并版 **CMM 1.21.1-3.0**（Y-Xiao233/CreateMoreMachines，无独立发布渠道）：分级破碎轮、分级机械锯（含研磨路径）、分级鼓风机全部改挂在 `createmoremachines` 命名空间与 `net.yxiao233.createmoremachines` 包下，`BuiltInAdvancedMachineTypes` 新增 `CRUSHING_WHEEL`/`SAW`/`ENCASED_FAN` 一等类型。本模组现有的「CMMM 可选兼容层」（反射桥接、类名前缀探测、独立 mixin 配置）已无存在必要，且其针对 CMMM 1.0/1.1 内部结构的注入点与 3.0 的实现不再对应。需要重写为对 CMM 3.0 的原生支持，并删除兼容层。

## What Changes

- **BREAKING**：最低 CMM 版本从 2.7 提升到 **1.21.1-3.0**；不再支持「CMM 2.7 + 独立 CMMM」的旧组合（独立 CMMM modid 不再被识别）。
- 删除整个 `compat/createmoremoremachines/` 兼容层（Tier 桥接、JEI 桥接、CMMM mixin 配置与插件、破碎轮 mixin），破碎轮/锯的门槛判定改为对 CMM 3.0 类型的直接识别（CMM 是必需依赖，可安全直接引用）。
- `crushing`/`milling`/`cutting` 三类配方的门槛与 JEI 分级分类从「依赖可选 CMMM」变为**随 CMM 3.0 常驻可用**；JEI 侧机器解析改走 `BuiltInAdvancedMachineTypes.CRUSHING_WHEEL`/`SAW`，删除反射桥接。
- 运行等级可调名单更新为 CMM 3.0 的机器类（破碎轮控制器、CMM 分级锯的新包名；CMM 锯条目修正为 `CMMMechanicalSawBlockEntity`）；鼓风机按用户决定**本轮不纳入**（保持不参与等级体系，排除原因更新为「门槛 API 仍无机器上下文」）。
- Mekanical-Create 兼容的模块识别从双命名空间（`createmoremachines` + `createmoremoremachines`）收敛为**仅 `createmoremachines`**。
- 依赖与构建：`libs/` 换用从上游源码构建的 `createmoremachines-1.21.1-3.0.jar`，删除 CMMM jar、`createmoremoremachines_version` 属性与 mods.toml 可选依赖条目。
- 离线回归同步改写：CMMM 类名前缀断言改为 CMM 3.0 类识别断言，名单条目数随新名单调整。

## Capabilities

### New Capabilities

（无 —— 全部为既有能力的修改或退役。）

### Modified Capabilities

- `machine-gates`: 「可选分级机器的识别隔离」需求改为对 CMM 3.0 分级机器（破碎轮/控制器/锯）的直接识别；破碎/研磨/锯切门槛不再依赖可选 mod 在场；破碎轮 mixin 注入点按 3.0 控制器结构重做并保持产量放大。
- `jei-integration`: crushing/milling/cutting 分级分类与序列装配分级锯渲染改为随 CMM 3.0 常驻（删除「CMMM 缺席时行为与旧版一致」分支），机器解析走 CMM 3.0 一等类型。
- `run-tier`: 可调名单更新为 CMM 3.0 类（含修正锯条目类名）；名单外排除项保留鼓风机并更新排除原因。
- `compat/mekanicalcreate`: 双命名空间识别收敛为仅 `createmoremachines`；CMMM 命名空间物品不再映射等级与倍率。

## Impact

- **代码**：删除 `src/main/java/org/minecart/more_formula/compat/createmoremoremachines/`（6 个文件）与 `more_formula-createmoremoremachines.mixins.json`；改写 `TierHelper`（识别/择优）、`MoreFormulaJeiPlugin`（机器解析）、`MachineRoster`/`CrushingWheelPair`（类名）、`MekanicalCreateRecipeGate`（命名空间）、新增 CMM 3.0 破碎轮控制器 mixin（替换 CMMM 版）；`More_formula.java`/`mods.toml` 模板/build.gradle/gradle.properties 同步。
- **依赖**：`libs/createmoremachines-1.21.1-3.0.jar`（需从 Y-Xiao233/CreateMoreMachines 源码构建，仓库无 release）；移除 `createmoremoremachines-1.21.1-1.1.jar`。
- **迁移**：门槛表以配方 ID 记录、不涉及机器身份，玩家门槛配置不受影响；运行等级附件绑定方块实体，CMM 3.0 若更换方块实体类型则旧附件不随迁移（随机器破坏语义丢失，属可接受损失）；Mekanical-Create 场景里旧 `createmoremoremachines` 命名空间模块物品停止享受倍率，需替换为 3.0 的 `createmoremachines` 模块。
- **规格退役**：`compat/createmoremoremachines` 能力整体移除（`retire_capabilities: true`），其尚且成立的行为并入 `machine-gates`/`jei-integration`。
