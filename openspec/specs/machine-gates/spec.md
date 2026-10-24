# machine-gates Specification

## Purpose

定义机器侧配方闸门：哪些 Create 配方类型在执行时受等级门槛拦截、机器等级如何识别，以及浇注、机械手、序列装配等特殊路径的判定行为。门槛模型本身见 `tier-gating`。

## Requirements

### Requirement: 受闸门的配方类型

系统 SHALL 对以下 Create 配方类型执行门槛判定：`create:pressing`（辊压）、`create:mixing`（搅拌）、`create:compacting`（压实）、`create:filling`（注液）、`create:deploying`（机械手）、`create:item_application`（拿物装配）、`create:sequenced_assembly`（序列装配）。未设门槛的配方行为 SHALL 与未安装本模组时完全一致。

#### Scenario: 未设门槛配方不受影响

- **WHEN** 任意上述类型的配方未设门槛
- **THEN** 原版 Create 机器与 CMM 高级机器均可正常处理，行为与未安装本模组时一致

### Requirement: 破碎、研磨与锯切的原版拒绝

原版 Create 破碎轮、磨石与机械锯按 0 级参与门槛判定：被设门槛的 `create:crushing`/`create:milling`/`create:cutting`（含切石）配方 SHALL 被这些机器拒绝。破碎轮按其原生「无配方」行为在倒计时后销毁物品；磨石与锯按「无匹配配方」无损退回；磨石在同一输入命中多条配方时，只要任一条带门槛即拒绝插入。分级机器的判定见 `compat/createmoremoremachines`。

#### Scenario: 原版磨石拒绝带门槛配方

- **WHEN** 某 milling 配方被设门槛，物品尝试进入原版磨石
- **THEN** 物品无法被插入，留在来源处

#### Scenario: 原版锯无损退回

- **WHEN** 某 cutting 配方被设门槛，物品送入原版机械锯
- **THEN** 该配方被过滤，物品短倒计时后原样退回

### Requirement: 机器等级识别

系统 SHALL 将原版 Create 机器（及任何非 CMM、非已识别分级机器的方块实体）识别为 0 级；CMM 高级机器 SHALL 按其自身档位（黄铜/下界合金/末影/超越/创造）一一对应到门槛等级 1..4 与 -1。等级换算对整合包作者透明，脚本侧只使用 `Tier` 常量。

#### Scenario: 原版机器被门槛配方拒绝

- **WHEN** 配方门槛为 `Tier.BRASS`（1），处理机器为原版 Create 动力辊压机（0 级）
- **THEN** 配方不被执行，机器按「无匹配配方」处理

#### Scenario: 高档 CMM 机器放行

- **WHEN** 配方门槛为 `Tier.NETHERITE`（2），处理机器为末影级（3 级）CMM 高级机器
- **THEN** 配方正常执行

#### Scenario: 未知第三方机器视为 0 级

- **WHEN** 处理机器既非 CMM 高级机器、也非已识别的可选分级机器
- **THEN** 该机器按 0 级参与判定，只能执行无门槛配方

### Requirement: 浇注候选取最严门槛

判定注液器（喷口）能否浇注某物品时，系统 SHALL 对「该物品 + 该流体可能命中的所有浇注配方候选」（含序列装配展开出的浇注工序）中最严的门槛进行判定，而不是任取其中一条的门槛。

#### Scenario: 混合候选按最严判定

- **WHEN** 同一物品与流体同时命中一条无门槛浇注配方和一条 `Tier.END` 门槛浇注配方，机器等级低于 3
- **THEN** 浇注被拒绝（宁可拦得严，不可漏放）

### Requirement: 机械手与拿物装配拦截

机械手执行配方搜索时，系统 SHALL 对选中的配方做门槛判定；不满足门槛的配方 SHALL 被取消、不被机械手执行。拿物装配（`create:item_application`）遵循同一规则。

#### Scenario: 机械手不执行越门槛配方

- **WHEN** 机械手（0 级或低档）选中了一条门槛高于其等级的配方
- **THEN** 该配方被取消执行，机械手不产出结果

### Requirement: 序列装配门槛作用于展开工序

序列装配配方设置的门槛 SHALL 同样作用于其展开出的各道工序配方（辊压、注液、装配、锯切等），按执行该工序的机器等级判定。

#### Scenario: 低级机器无法执行设门槛的装配工序

- **WHEN** 序列装配配方设 `Tier.BEYOND`（4）门槛，其展开的辊压工序由低于 4 级的机器执行
- **THEN** 该道工序不执行

### Requirement: 可选分级机器的识别隔离

对可选依赖提供的分级机器（见 `compat/createmoremoremachines`），系统 SHALL 仅在该模组确实在场时才识别其等级；识别失败或模组缺席时按 0 级处理，且 MUST NOT 因加载可选模组的类而导致崩溃。

#### Scenario: 桥接失败按 0 级且不崩溃

- **WHEN** 可选分级机器的等级解析失败
- **THEN** 该机器按 0 级判定（带门槛的配方被拒绝），游戏继续运行并只警告一次
