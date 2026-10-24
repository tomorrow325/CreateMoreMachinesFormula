# Spec Delta

## REMOVED Requirements

### Requirement: 分级模块的双命名空间识别

**Reason**: 独立 CMMM 已并入 CMM 3.0，`createmoremoremachines` 命名空间停止识别。
**Migration**: 由本增量 ADDED 的「分级模块的命名空间识别」需求承接；旧 CMMM 命名空间模块物品需替换为 CMM 3.0 的 `createmoremachines` 模块方可享受倍率。

## ADDED Requirements

### Requirement: 分级模块的命名空间识别

兼容层 SHALL 把 `createmoremachines` 命名空间下、名称以 `creative_`/`brass_`/`netherite_`/`end_`/`beyond_` 为前缀的机器物品（deployer、mechanical_press、mechanical_mixer、spout、crushing_wheel、mechanical_saw）识别为对应等级（-1/1/2/3/4）的模块；其他命名空间（含已退役的 `createmoremoremachines`）的物品按 0 级、不参与加成映射。

#### Scenario: CMM 3.0 分级模块识别

- **WHEN** 模块物品 ID 位于 `createmoremachines` 命名空间且带档位前缀
- **THEN** 其被识别为对应等级的模块，享受速度/并行倍率

#### Scenario: 旧 CMMM 命名空间物品不再识别

- **WHEN** 模块物品 ID 位于 `createmoremoremachines` 命名空间（独立 CMMM 时期的旧物品）
- **THEN** 识别结果为 0 级（NONE），不参与任何加成映射

#### Scenario: 无关物品不识别

- **WHEN** 模块物品无档位前缀或属于其他命名空间
- **THEN** 识别结果为 0 级（NONE），不参与任何加成映射
