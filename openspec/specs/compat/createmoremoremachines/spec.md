# compat/createmoremoremachines Specification

## Purpose

定义可选依赖 CreateMoreMoreMachines（1.21.1-1.0+，CMMM）的兼容行为：分级破碎轮、分级磨石（由分级破碎轮控制器执行）与分级机械锯纳入等级门槛，及其在 JEI 中的分级展示；以及模组缺席时的隔离保证。

## Requirements

### Requirement: 分级机器纳入门槛判定

CMMM 的分级破碎轮、分级破碎轮控制器与分级机械锯 SHALL 按 CMM 同语义的档位（黄铜/下界合金/末影/超越/创造对应 1..4 与 -1）参与门槛判定，使 `create:crushing`、`create:milling`、`create:cutting`（含切石）三类配方可以设置门槛。

#### Scenario: 分级锯按档位放行

- **WHEN** `create:cutting` 配方设 `Tier.NETHERITE` 门槛，由末影级（3 级）分级机械锯处理
- **THEN** 配方正常执行

#### Scenario: 未装 CMMM 时这三类不可设门槛

- **WHEN** 游戏实例未安装 CMMM
- **THEN** 不存在可执行门槛判级的分级破碎轮/磨石/锯，给这三类配方设门槛将导致其无法被任何机器执行

### Requirement: 破碎轮不满足门槛时销毁物品

分级破碎轮对不满足门槛的破碎/研磨配方 SHALL 保持原生表现：按「无匹配配方」处理，物品在倒计时结束后被研磨掉（销毁），不存在「原样退回」。

#### Scenario: 低级破碎轮销毁物品

- **WHEN** `create:crushing` 配方设 `Tier.END` 门槛，物品投入低于 3 级的分级破碎轮
- **THEN** 物品倒计时结束后被销毁，不产出、不退回

### Requirement: 机械锯与磨石不满足门槛时原样退回

分级机械锯与磨石（分级破碎轮控制器的研磨路径）对不满足门槛的配方 SHALL 按「无匹配配方」处理，物品无损原样退回。

#### Scenario: 低级锯退回物品

- **WHEN** `create:cutting` 配方设 `Tier.BEYOND` 门槛，物品送入低于 4 级的分级机械锯
- **THEN** 物品不被加工，原样退回

### Requirement: JEI 分级分类与动画

CMMM 在场时，设了门槛的 crushing/milling/cutting 配方 SHALL 进入 more_formula 的分级分类（带分级破碎轮/分级锯动画渲染），并从原版 Create 对应分类隐藏（锯切的原版分类为 `create:sawing`；milling 的催化剂为分级破碎轮）。序列装配的锯切工序 SHALL 改用分级锯渲染，且分级锯作为序列装配分类的催化剂显示。

#### Scenario: 设门槛的锯切进分级分类

- **WHEN** CMMM 在场且某 `create:cutting` 配方设了门槛
- **THEN** 该配方从 `create:sawing` 分类隐藏，出现在对应等级的分级锯分类并带分级锯动画

#### Scenario: 序列装配的锯切工序渲染分级锯

- **WHEN** CMMM 在场，序列装配含锯切工序且设了门槛
- **THEN** 该工序在 JEI 中以分级锯渲染，分级锯同时出现在催化剂列表

### Requirement: 模组缺席时行为与旧版一致

未安装 CMMM 时，本兼容 SHALL 完全不生效：游戏正常启动、无报错、桥接类不被加载；JEI 不建破碎/研磨/锯切的分级分类、不隐藏原版分类，序列装配锯切工序使用 Create 原版锯渲染，与未引入 CMMM 支持前的版本行为完全一致。

#### Scenario: 缺席 CMMM 时 JEI 行为不变

- **WHEN** 游戏实例未安装 CMMM
- **THEN** 游戏正常启动，JEI 中破碎/研磨/锯切配方全部留在原版分类，无分级分类、无隐藏
