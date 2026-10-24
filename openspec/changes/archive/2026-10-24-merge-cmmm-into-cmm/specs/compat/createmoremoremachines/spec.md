# Spec Delta

## REMOVED Requirements

### Requirement: 分级机器纳入门槛判定

**Reason**: CMMM 已并入 CMM 1.21.1-3.0，分级破碎轮/机械锯成为必需依赖下的原生机器，不再存在「可选依赖」语义。
**Migration**: 该行为由 `machine-gates` 的「分级破碎轮与机械锯的原生识别」需求承接；`crushing`/`milling`/`cutting` 门槛随 CMM 3.0 常驻可用。

### Requirement: 破碎轮不满足门槛时销毁物品

**Reason**: 同上，行为不变、归属改变。
**Migration**: 由 `machine-gates` 的「分级破碎轮与机械锯的原生识别」需求中的销毁场景承接。

### Requirement: 机械锯与研磨路径不满足门槛时原样退回

**Reason**: 同上，行为不变、归属改变。
**Migration**: 由 `machine-gates` 的「分级破碎轮与机械锯的原生识别」需求中的退回场景承接（研磨走破碎轮控制器语义）。

### Requirement: JEI 分级分类与动画

**Reason**: 分级破碎轮/锯动画不再依赖可选 mod 在场。
**Migration**: 由 `jei-integration` 新增的「破碎/研磨/锯切的常驻分级分类」需求承接。

### Requirement: 择优保留分级破碎轮产量放大

**Reason**: 产量放大（`getProcessingMultiple`）已内置于 CMM 3.0 破碎轮控制器本体。
**Migration**: 由 `machine-gates` 新增的「破碎轮产量放大保持」需求承接。

### Requirement: 模组缺席时行为与旧版一致

**Reason**: 可选依赖不复存在（CMM 3.0 为必需依赖），「缺席分支」整体失效。
**Migration**: 无需迁移；未安装 CMM 3.0 时 NeoForge 依赖检查直接拒绝加载本模组。
