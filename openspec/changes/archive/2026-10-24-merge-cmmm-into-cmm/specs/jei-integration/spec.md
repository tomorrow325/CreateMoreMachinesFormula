# Spec Delta

## ADDED Requirements

### Requirement: 破碎/研磨/锯切的常驻分级分类

安装 JEI 时，crushing/milling/cutting 的分级分类与分级动画 SHALL 随 CMM 3.0 常驻可用，不再依赖任何可选 mod 在场：机器与催化剂经 CMM 3.0 的 `BuiltInAdvancedMachineTypes`（`crushing_wheel`/`saw`）按等级解析，milling 的催化剂为分级破碎轮；设了门槛的配方从原版 Create 对应分类隐藏（锯切的原版分类为 `create:sawing`）；序列装配的锯切工序以分级锯渲染，且分级锯作为序列装配分类的催化剂显示。

#### Scenario: 无可选依赖也有分级分类

- **WHEN** 仅安装必需依赖（Create + CMM 3.0 + KubeJS 系）与 JEI，某 `create:cutting` 配方设了门槛
- **THEN** 该配方从 `create:sawing` 隐藏，出现在对应等级的分级锯分类并带分级锯动画

#### Scenario: 序列装配的锯切工序渲染分级锯

- **WHEN** 序列装配含锯切工序且设了门槛
- **THEN** 该工序在 JEI 中以分级锯渲染，分级锯同时出现在催化剂列表
