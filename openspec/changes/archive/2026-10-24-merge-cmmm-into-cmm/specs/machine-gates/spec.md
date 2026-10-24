# Spec Delta

## REMOVED Requirements

### Requirement: 可选分级机器的识别隔离

**Reason**: CMMM 并入 CMM 3.0 后不存在可选分级机器，反射桥接与「识别失败按 0 级」分支整体失效。
**Migration**: 由本增量 ADDED 的「分级破碎轮与机械锯的原生识别」需求承接（必需依赖下直接识别，无失败分支）。

## MODIFIED Requirements

### Requirement: 破碎、研磨与锯切的原版拒绝

原版 Create 破碎轮、磨石与机械锯按 0 级参与门槛判定：被设门槛的 `create:crushing`/`create:milling`/`create:cutting`（含切石）配方 SHALL 被这些机器拒绝。破碎轮按其原生「无配方」行为在倒计时后销毁物品；磨石与锯按「无匹配配方」无损退回；磨石在同一输入命中多条配方时，只要任一条带门槛即拒绝插入。分级机器的判定见「分级破碎轮与机械锯的原生识别」。

#### Scenario: 原版磨石拒绝带门槛配方

- **WHEN** 某 milling 配方被设门槛，物品尝试进入原版磨石
- **THEN** 物品无法被插入，留在来源处

#### Scenario: 原版锯无损退回

- **WHEN** 某 cutting 配方被设门槛，物品送入原版机械锯
- **THEN** 该配方被过滤，物品短倒计时后原样退回

## ADDED Requirements

### Requirement: 分级破碎轮与机械锯的原生识别

CMM 3.0 的分级破碎轮（含其控制器）与分级机械锯 SHALL 作为必需依赖下的原生机器参与门槛判定，档位（黄铜/下界合金/末影/超越/创造）对应门槛等级 1..4 与 -1，等级换算与既有 CMM 机器一致；`create:crushing`、`create:milling`、`create:cutting`（含切石）三类配方的门槛不再依赖任何可选 mod 在场。破碎轮（含研磨路径）不满足门槛时按原生语义销毁物品；机械锯不满足门槛时按「无匹配配方」无损退回。CMM 3.0 之下不存在分级磨石方块，milling 由破碎轮控制器承载。

#### Scenario: 分级锯按档位放行

- **WHEN** `create:cutting` 配方设 `Tier.NETHERITE` 门槛，由末影级（3 级）分级机械锯处理
- **THEN** 配方正常执行

#### Scenario: 低级破碎轮销毁物品

- **WHEN** `create:crushing` 配方设 `Tier.END` 门槛，物品投入低于 3 级的分级破碎轮
- **THEN** 物品倒计时结束后被销毁，不产出、不退回

#### Scenario: 低级锯退回物品

- **WHEN** `create:cutting` 配方设 `Tier.BEYOND` 门槛，物品送入低于 4 级的分级机械锯
- **THEN** 物品不被加工，原样退回

#### Scenario: 研磨走破碎轮语义

- **WHEN** `create:milling` 配方设门槛，物品投入等级不足的分级破碎轮
- **THEN** 按破碎轮原生语义在倒计时后销毁，不存在「磨石退回」路径

### Requirement: 破碎轮产量放大保持

CMM 3.0 破碎轮控制器自带的按等级产量放大（`getProcessingMultiple`，放大产出与处理量）SHALL 在本模组门槛判定与择优介入后照常生效：门槛与择优只决定「找到/执行哪条配方」，MUST NOT 跳过或削弱该放大。

#### Scenario: 高档破碎轮仍多产

- **WHEN** 超越级分级破碎轮执行被择优选中的 crushing 配方
- **THEN** 产出按 CMM 3.0 对应等级的倍率放大，与未装本模组的产量一致
