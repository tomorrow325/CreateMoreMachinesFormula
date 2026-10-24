# compat/mekanicalcreate Specification

## Purpose

定义可选依赖 Mekanical-Create（0.2.8+，需 Mekanism）的兼容行为：CMM/CMMM 分级机器物品作为其机器模块时的等级识别、配方门槛过滤、速度/并行倍率与 JEI 最低适用档展示；以及模组缺席时的隔离保证。

## Requirements

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

### Requirement: 模块按门槛过滤可执行配方

模块 SHALL 只允许其等级满足门槛的配方被执行：普通门槛要求模块等级 ≥ 门槛，创造级门槛只匹配创造模块，创造模块通行一切门槛。机器种类需与配方种类对应（deployer 对应装配、press 对应辊压、mixer/spout 对应流体工序、crushing_wheel/saw 对应破碎/锯切）。

#### Scenario: 低档模块不执行高门槛配方

- **WHEN** `Tier.NETHERITE` 门槛配方由黄铜级（1 级）模块处理
- **THEN** 该配方被过滤，不被执行

#### Scenario: 创造门槛只匹配创造模块

- **WHEN** `Tier.CREATIVE` 门槛配方由超越级（4 级）模块处理
- **THEN** 该配方被过滤

### Requirement: JEI 按最低适用档过滤

Mekanical-Create 的 JEI 视图中，每个配方 SHALL 只显示刚好满足其门槛的最低档机器（创造级门槛只显示创造模块），高级档位不再重复刷屏；被双重派生的候选 ID SHALL 能还原出原配方 ID 用于门槛解析。

#### Scenario: 最低档去重展示

- **WHEN** 某 `Tier.BRASS` 门槛配方存在黄铜、末影、超越、创造四档可执行的派生条目
- **THEN** JEI 只显示黄铜档机器对应的条目

### Requirement: 速度与并行倍率

装有 Mekanical-Create 时，启用中的分级模块 SHALL 提供速度/并行加成：模拟腔的并行 lane 数按模块倍率放大；工厂多方块每 tick 的 work budget 按最高启用催化剂的倍率放大，单条配方的总能耗 MUST NOT 被重复放大；多个催化剂同时生效时取最高倍率，MUST NOT 相乘。倍率按 6 类机器（压机/部署器/搅拌器/喷口/破碎轮/机械锯）× 5 个等级在服务器 TOML 配置中调整，取值范围 1–64，默认 1/2/3/4/4（创造级同超越级默认 4）。

#### Scenario: 并行 lane 放大

- **WHEN** 模拟腔插入倍率 3 的模块
- **THEN** 其并行 lane 数按 3 倍运行

#### Scenario: 多催化剂取最高

- **WHEN** 工厂同时启用倍率 2 与倍率 4 的催化剂
- **THEN** work budget 按 4 倍（最高者）放大，而非 8 倍

### Requirement: 模组缺席不生效

未安装 Mekanical-Create 时，本兼容 SHALL 完全不生效：游戏正常启动、无报错，倍率配置段不产生作用，其余功能不受影响。

#### Scenario: 缺席 Mekanical-Create 正常启动

- **WHEN** 游戏实例未安装 Mekanical-Create
- **THEN** 游戏正常启动，日志无兼容层错误，门槛功能照常工作
