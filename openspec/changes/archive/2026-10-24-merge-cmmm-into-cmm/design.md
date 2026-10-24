# Design

## Context

见 proposal.md 的 Why。上游合并版 CMM 1.21.1-3.0 已勘察（源码 Y-Xiao233/CreateMoreMachines master）：CMMM 机器类**类名不变、包名改为** `net.yxiao233.createmoremachines.api.content.*`；`BuiltInAdvancedMachineTypes` 新增 `SAW`/`CRUSHING_WHEEL`/`ENCASED_FAN` 一等类型；破碎轮控制器自带 `getTier()` 与 `getProcessingMultiple` 产量放大，`findRecipe` 内部仍是「先 CRUSHING.find、空则 MILLING.find」的双查找结构；2.7 的压机/搅拌器/部署器/喷口类原样保留。仓库无 release，jar 需从源码构建。

## Goals / Non-Goals

**Goals:**

- 删除 CMMM 可选兼容层，改为对 CMM 3.0 的直接依赖式支持；破碎轮/锯的门槛、择优、JEI、运行等级全部行为等价迁移。
- 依赖声明收敛：CMM 下限提升到 1.21.1-3.0，CMMM 相关构建配置与 mods.toml 条目移除。

**Non-Goals:**

- 分级鼓风机门槛化（用户决定本轮不碰；`ENCASED_FAN` 类型与机器照常工作，仅不参与等级体系）。
- 兼容「CMM 2.7 + 独立 CMMM」旧组合（用户决定直接切换，不做双兼容）。
- 不改门槛表/KubeJS API/JEI 既有分类结构等与合并无关的行为。

## Decisions

- **直接 instanceof 取代「类名前缀 + 反射桥接」**：CMM 是必需依赖，`TierHelper`/`MachineRoster`/`CrushingWheelPair` 可直接引用 `net.yxiao233...crushing_wheel/mechanical.saw` 的类。反射桥（`CMMMTierBridge`）、包名前缀探测（`isCMMMClassName`）、warn-once 兜底全部删除，识别失败分支不复存在。备选「保留字符串匹配以少改代码」被否决：必需依赖下没有缺席分支，字符串匹配只剩维护成本。
- **破碎轮闸门沿用「内部 find 替换」注入策略，目标是 3.0 控制器**：3.0 的产量放大（`getMultiple()`）在 find 之后的处理路径里，若在 `findRecipe` RETURN 整体替换会再次跳过放大（0.1.1 修过的同类问题）。因此继续在控制器内部 `AllRecipeTypes.find` 调用处做 `@ModifyExpressionValue`（CRUSHING 与 MILLING 两处同描述符一次覆盖）。3.0 控制器源码已确认存在同形结构；**具体方法名/描述符以构建出的 jar 字节码核对为准**（任务 3.1）。
- **分级锯闸门优先复用原版锯 mixin**：若 3.0 的 `CMMMechanicalSawBlockEntity` 仍继承 Create 的 `SawBlockEntity`，现有 `SawBlockEntityMixin`（过滤 + 择优排序 + recipeIndex 归零）自动作用之，无需新 mixin；若不继承则新建针对 3.0 锯的 mixin。以 jar 反编译结论为准（任务 3.2）。
- **JEI 机器解析改走一等类型**：`MoreFormulaJeiPlugin` 的 `machine()` 直接对 `BuiltInAdvancedMachineTypes.CRUSHING_WHEEL`/`SAW` 取各档条目（与 PRESS/MIXER 同一写法），`CMMMJeiBridge`、FML 在场探测、自检逻辑删除；crushing/milling/cutting 分类与序列装配分级锯上下文从「CMMM 在场才建」变为常驻构建。
- **`createmoremoremachines` 命名空间识别直接删除**（Mekanical-Create 兼容）：不做存量物品兼容，旧模块物品按 NONE 处理并在 README 迁移说明中告知。
- **构建产物命名**：沿用 BUILD.md 的既有做法，从上游源码 `gradlew build` 出 `createmoremachines-1.21.1-3.0.jar` 放入 `libs/`（上游无 maven 渠道），删除 `createmoremoremachines-1.21.1-1.1.jar`。

> **1.2 核对结论（已回填）**：构建出的 `createmoremoremachines-1.21.1-3.0.jar` 与源码快照完全一致 —— 控制器 `findRecipe` 为「先 CRUSHING.find、空则 MILLING.find」双查找结构（速度/产量放大在 find 之后，由 `getSpecial(speed)` 包装与 `BigItemStackHandler` 承担）；`CMMMechanicalSawBlockEntity` 继承 Create 的 `SawBlockEntity`（现有锯 mixin 自动覆盖）；`BuiltInAdvancedMachineTypes` 含 `CRUSHING_WHEEL`/`SAW`/`ENCASED_FAN` 一等类型。注入点与 wrapper 字段名（`createmoremachines$recipeWrapper`）均按此落地。

## Risks / Trade-offs

- [3.0 控制器/锯的内部结构与源码快照有出入（上游仍在提交）] → 任务 3.1/3.2 强制以 jar 字节码核对注入点后再写 mixin；发现结构变化时更新本设计记录。
- [**BREAKING**：CMM 下限 2.7→3.0，存量整合包需同时升级 CMM] → README 依赖表与更新说明明确标注；门槛表以配方 ID 记录，玩家配置无感。
- [旧 CMMM 运行等级附件/模块物品不迁移] → 运行等级本就随方块实体生命周期（破坏即失），换方块实体类视为机器更换，符合既有语义；Mekanical 模块需手动替换。
- [上游 3.0 与 Create 6.0.10 的兼容范围未验证] → apply 阶段 runClient/runClientNoOpt 双场景冒烟兜底。

## Open Questions

（无 —— 鼓风机与兼容策略已由用户定夺；注入点细节不改变行为规格，按任务内核对即可。）
