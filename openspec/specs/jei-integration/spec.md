# jei-integration Specification

## Purpose

定义安装 JEI 时设了门槛的配方在 JEI 中的展示行为：分级分类的构建时机与全集策略、原版分类的隐藏、运行时与门槛表同步、动画与催化剂展示，以及未安装 JEI 时的降级。

## Requirements

### Requirement: 分级分类按等级全集构建

安装 JEI 且配置了至少一个门槛时，系统 SHALL 为等级全集（1..4 与创造级 -1）构建分级配方分类，不依赖「JEI 建分类时脚本是否已执行」的时序；未配置任何门槛时 MUST NOT 创建任何分级分类。

#### Scenario: 无门槛配置不建分类

- **WHEN** 整合包未配置任何门槛
- **THEN** JEI 中不出现 more_formula 的分级分类

#### Scenario: 脚本晚于 JEI 建分类注册的等级也有分类

- **WHEN** JEI 构建分类时某等级尚未被任何门槛引用，随后脚本注册了该等级
- **THEN** 该等级的分级分类在 JEI 就绪时变为可见，无需重建分类

### Requirement: 已设门槛配方从原版分类隐藏

被设了门槛的配方 SHALL 从 Create 原版对应 JEI 分类中隐藏，并出现在与其门槛等级对应的分级分类中，避免同一配方在两处重复出现。锯切的原版分类为 `create:sawing`。

#### Scenario: 辊压配方进分级分类

- **WHEN** 某 `create:pressing` 配方被设 `Tier.BRASS` 门槛
- **THEN** 该配方在原版压机分类中隐藏，在 more_formula 的黄铜级压机分类中可见

### Requirement: 运行时按门槛表同步显示

JEI 每次就绪时，系统 SHALL 按当前门槛表：隐藏未被使用的等级分类、取消隐藏在用等级分类，并为「建分类时未注册、之后才出现」的等级补交配方；已提交过配方的等级 MUST NOT 被重复提交。

#### Scenario: 重载新增等级后分类点亮

- **WHEN** `/reload` 后门槛表新增了 `Tier.END` 等级条目，JEI 再次就绪
- **THEN** 末影级分级分类取消隐藏并展示对应配方

#### Scenario: 不重复提交配方

- **WHEN** JEI 多次就绪且某等级的配方早已提交
- **THEN** 该等级分类中的配方不重复出现

### Requirement: 分级动画与催化剂展示

分级分类 SHALL 以对应等级的 CMM 高级机器渲染配方动画（辊压机、搅拌器、喷口、机械手、装配线等）。每个分类的催化剂 SHALL 列出「该门槛等级及以上各档（含创造级）」的对应机器；创造级门槛的分类只展示创造级机器作为催化剂。

#### Scenario: 普通门槛的催化剂含高档机器

- **WHEN** 查看 `Tier.BRASS` 门槛的压机分级分类
- **THEN** 催化剂包含黄铜、下界合金、末影、超越与创造各档高级压机

#### Scenario: 创造门槛只列创造机器

- **WHEN** 查看 `Tier.CREATIVE` 门槛的分级分类
- **THEN** 催化剂只包含创造级机器

### Requirement: 未安装 JEI 时功能降级

JEI 为可选依赖。未安装 JEI 时，系统 SHALL 正常启动，机器闸门与 KubeJS 配置面不受影响，仅无分级展示。

#### Scenario: 缺席 JEI 不影响核心功能

- **WHEN** 游戏实例未安装 JEI
- **THEN** 游戏正常启动，门槛判定与 `/reload` 行为与装有 JEI 时一致

### Requirement: 破碎/研磨/锯切的常驻分级分类

安装 JEI 时，crushing/milling/cutting 的分级分类与分级动画 SHALL 随 CMM 3.0 常驻可用，不再依赖任何可选 mod 在场：机器与催化剂经 CMM 3.0 的 `BuiltInAdvancedMachineTypes`（`crushing_wheel`/`saw`）按等级解析，milling 的催化剂为分级破碎轮；设了门槛的配方从原版 Create 对应分类隐藏（锯切的原版分类为 `create:sawing`）；序列装配的锯切工序以分级锯渲染，且分级锯作为序列装配分类的催化剂显示。

#### Scenario: 无可选依赖也有分级分类

- **WHEN** 仅安装必需依赖（Create + CMM 3.0 + KubeJS 系）与 JEI，某 `create:cutting` 配方设了门槛
- **THEN** 该配方从 `create:sawing` 隐藏，出现在对应等级的分级锯分类并带分级锯动画

#### Scenario: 序列装配的锯切工序渲染分级锯

- **WHEN** 序列装配含锯切工序且设了门槛
- **THEN** 该工序在 JEI 中以分级锯渲染，分级锯同时出现在催化剂列表
