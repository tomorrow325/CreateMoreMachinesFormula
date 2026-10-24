# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: 机械锯与磨石不满足门槛时原样退回`
- TO: `### Requirement: 机械锯与研磨路径不满足门槛时原样退回`

## MODIFIED Requirements

### Requirement: 机械锯与研磨路径不满足门槛时原样退回

分级机械锯对不满足门槛的配方 SHALL 按「无匹配配方」处理，物品无损原样退回。CMM 与 CMMM 均不存在分级磨石方块：`create:milling` 的分级执行由**分级破碎轮控制器**承载（与 crushing 同一拦截点），其不满足门槛时的表现遵循破碎轮的原生语义（销毁），研磨路径不存在独立的「磨石退回」行为。

#### Scenario: 低级锯退回物品

- **WHEN** `create:cutting` 配方设 `Tier.BEYOND` 门槛，物品送入低于 4 级的分级机械锯
- **THEN** 物品不被加工，原样退回

#### Scenario: 研磨走破碎轮语义

- **WHEN** `create:milling` 配方设门槛，物品投入等级不足的分级破碎轮
- **THEN** 按破碎轮原生语义在倒计时后销毁，不存在「磨石退回」路径

## ADDED Requirements

### Requirement: 择优保留分级破碎轮产量放大

CMMM 自身按等级放大破碎产量（倍率 > 1 时放大产出并缩短时长）的后处理 SHALL 在本模组择优介入后照常生效：门槛判定与择优只决定「找到哪条配方」，MUST NOT 跳过或削弱 CMMM 的产量放大。

#### Scenario: 高档破碎轮仍多产

- **WHEN** 超越级分级破碎轮执行被择优选中的 crushing 配方
- **THEN** 产出按 CMMM 对应等级的倍率放大，与未装本模组的产量一致
