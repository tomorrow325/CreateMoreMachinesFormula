# tier-gating Specification

## Purpose

定义配方等级门槛的数据模型与判定规则：门槛如何登记与解析、如何随 KubeJS 脚本重载演化，以及「一台机器能否执行某条配方」的判定语义。这是全部机器闸门与展示行为的基础。

## Requirements

### Requirement: 等级常量语义

系统 SHALL 用封闭的等级集合表达门槛与机器档位：`Tier.ZERO`（0，无门槛）、`Tier.BRASS`（1）、`Tier.NETHERITE`（2）、`Tier.END`（3）、`Tier.BEYOND`（4）四级普通等级，以及 `Tier.CREATIVE`（-1，特殊等级）。普通门槛的判定规则 SHALL 为「机器等级 ≥ 门槛」；门槛为 0 时任何机器均可处理。

#### Scenario: 机器等级满足普通门槛

- **WHEN** 配方门槛为 `Tier.END`（3），机器等级为 3 或 4
- **THEN** 判定允许执行

#### Scenario: 机器等级低于普通门槛

- **WHEN** 配方门槛为 `Tier.END`（3），机器等级为 1 或 2
- **THEN** 判定拒绝执行

#### Scenario: 无门槛配方不设限

- **WHEN** 配方未设门槛（等效 `Tier.ZERO`）
- **THEN** 任何机器（含原版 Create 机器）均可处理

### Requirement: 创造级门槛的特殊判定

门槛为 `Tier.CREATIVE`（-1）时，系统 SHALL 只允许等级为 -1 的创造级机器执行，其余所有机器（含普通四级高级机器）一律拒绝；反之，创造级机器 SHALL 可通行一切门槛（含普通四级门槛）。

#### Scenario: 创造门槛只认创造机器

- **WHEN** 配方门槛为 `Tier.CREATIVE`，机器为超越级（4）高级机器
- **THEN** 判定拒绝执行

#### Scenario: 创造机器通行普通门槛

- **WHEN** 配方门槛为 `Tier.BEYOND`（4），机器为创造级（-1）
- **THEN** 判定允许执行

### Requirement: 精确与前缀门槛条目

系统 SHALL 支持按配方 ID 精确登记门槛，以及按 ID 前缀批量登记门槛（前缀结尾的 `*` SHALL 归一化去掉）。非法等级值（不在 -1 与 1..4 之内）与空前缀 MUST NOT 被登记；门槛 0 不作为条目保存。

#### Scenario: 前缀通配登记

- **WHEN** 以前缀 `create:mixing/` 登记门槛，随后查询 `create:mixing/brass_ingot` 的门槛
- **THEN** 返回该前缀登记的等级

#### Scenario: 非法登记被忽略

- **WHEN** 以等级 0、等级 7 或空前缀登记门槛
- **THEN** 不产生任何条目，查询任意配方返回无门槛

### Requirement: 同前缀重复登记幂等替换

系统 SHALL 保证同一前缀在同一来源下重复登记时替换旧条目，而非追加新条目。

#### Scenario: 重载后前缀条目不膨胀

- **WHEN** 同一前缀在同一来源被登记两次（如两次脚本执行）
- **THEN** 该前缀只对应一个条目，命中判定结果不变

### Requirement: 按来源分桶与热重载清理

门槛条目 SHALL 按登记来源分桶：服务器脚本（`server_scripts`）写入的条目在每次服务器脚本装载（含 `/reload`）前被整体清空后由脚本重建；启动脚本与启动事件写入的条目在整个游戏生命周期保留、不受 `/reload` 影响。客户端脚本装载 MUST NOT 清除服务器侧门槛。

#### Scenario: 服务器脚本条目随重载重建

- **WHEN** 修改服务器脚本中的门槛后执行 `/reload`
- **THEN** 新门槛立即生效，无需重启游戏，且旧条目不残留

#### Scenario: 启动事件条目跨重载保留

- **WHEN** 启动事件注册了门槛，随后执行多次 `/reload`
- **THEN** 该门槛持续生效且不重复累积

### Requirement: 门槛解析优先级

查询某配方 ID 的门槛时，系统 SHALL 按以下优先级解析：精确条目优先于前缀条目；同为精确或同为前缀时，服务器脚本来源优先于启动来源；多个前缀同时命中时取最长前缀。无任何命中时返回 0（无门槛）。

#### Scenario: 精确压过前缀

- **WHEN** `create:mixing/brass_ingot` 同时被前缀 `create:mixing/` 设为 1 级、被精确条目设为 3 级
- **THEN** 解析结果为 3

#### Scenario: 最长前缀胜出

- **WHEN** 同一来源下前缀 `create:` 设 1 级、前缀 `create:mixing/` 设 2 级，查询 `create:mixing/x`
- **THEN** 解析结果为 2

#### Scenario: 服务器脚本压过启动脚本

- **WHEN** 启动来源与服务器来源登记了等长的命中前缀且等级不同
- **THEN** 解析结果取服务器来源的等级

### Requirement: 已知等级集合

系统 SHALL 能枚举「当前被任何门槛条目引用过的等级」（精确与前缀条目均计入），结果升序、去重、不含 0。该集合是 JEI 分级分类决定显示哪些等级的依据。

#### Scenario: 仅前缀门槛的等级也被枚举

- **WHEN** 只通过前缀登记了 `Tier.BRASS` 门槛，没有任何精确条目
- **THEN** 已知等级集合包含 1
