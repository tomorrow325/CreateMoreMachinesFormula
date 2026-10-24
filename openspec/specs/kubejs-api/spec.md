# kubejs-api Specification

## Purpose

定义整合包作者通过 KubeJS 配置门槛的脚本面：全局绑定、配方链式修饰、按 ID/前缀登记、查询与移除，以及启动事件与热重载的时机语义。门槛数据模型见 `tier-gating`。

## Requirements

### Requirement: 全局绑定

系统 SHALL 在 KubeJS 脚本中提供全局绑定 `Tier`（常量 `ZERO`/`BRASS`/`NETHERITE`/`END`/`BEYOND`/`CREATIVE`）与 `MoreFormula`（静态方法 `setTier`、`setPrefixTier`、`removeTier`、`getTier`、`getAllTiers`、`getKnownTiers`），兼容 KubeJS-Create 的 Create 配方脚本 API。

#### Scenario: 脚本直接引用绑定

- **WHEN** 服务器脚本调用 `MoreFormula.setTier('create:mixing/brass_ingot', Tier.BRASS)`
- **THEN** 该配方的门槛被登记为 1，`MoreFormula.getTier` 查询返回 1

### Requirement: 配方链式 .tier() 修饰

通过 KubeJS-Create 创建的 Create 配方（`event.recipes.create.*`，覆盖 pressing/mixing/compacting/filling/deploying/item_application/sequenced_assembly）SHALL 支持链式 `.tier(Tier.X)` 设定门槛；未调用 `.tier()` 的配方无门槛。

#### Scenario: 创建时直接设门槛

- **WHEN** 脚本以 `.id('example:end_pressing').tier(Tier.END)` 创建压机配方
- **THEN** 该配方 ID 的门槛为 3，末端机器需 3 级及以上

#### Scenario: 序列装配整体设门槛

- **WHEN** 脚本对 `sequenced_assembly` 配方调用 `.tier(Tier.BEYOND)`
- **THEN** 整条装配线的工序按 4 级门槛判定

### Requirement: setTier 的前缀通配

`MoreFormula.setTier` SHALL 支持以 `*` 结尾的配方 ID 作为前缀通配（如 `create:mixing/*`），效果等同 `setPrefixTier`；不带 `*` 的 ID 按精确条目登记。

#### Scenario: 批量为命名空间下配方设门槛

- **WHEN** 调用 `MoreFormula.setTier('create:mixing/*', Tier.BRASS)`
- **THEN** 所有 `create:mixing/` 前缀的配方门槛解析为 1

### Requirement: 查询与移除

`MoreFormula.getTier(recipeId)` SHALL 返回按优先级规则解析后的门槛；`MoreFormula.removeTier(recipeId)` SHALL 删除该配方在所有来源下的精确条目，且当 ID 以 `*` 结尾时 SHALL 按 `setTier` 相同的归一化（去 `*`）删除所有来源下匹配该前缀的条目；移除不存在的条目 MUST 为无害空操作，MUST NOT 抛出异常。`getAllTiers` 与 `getKnownTiers` 分别返回精确条目全表与已知等级集合。启动事件 `MoreFormulaEvents.registerTier` 的 `event.removeTier` SHALL 遵循与 `MoreFormula.removeTier` 相同的精确/前缀通配语义。

#### Scenario: 移除后回到无门槛

- **WHEN** 对已设 `Tier.BRASS` 门槛的配方调用 `MoreFormula.removeTier`
- **THEN** 该配方精确条目被清除；若无其他命中条目，解析结果回到 0

#### Scenario: 通配移除前缀门槛

- **WHEN** 先以 `MoreFormula.setTier('create:mixing/*', Tier.BRASS)` 批量设门槛，再调用 `MoreFormula.removeTier('create:mixing/*')`
- **THEN** 所有来源下该前缀的条目被清除，`create:mixing/` 下的配方解析回到 0，且脚本不抛异常

#### Scenario: 移除未登记的前缀为空操作

- **WHEN** 调用 `MoreFormula.removeTier('somemod:never_registered/*')`
- **THEN** 无任何条目变化、无异常，脚本继续执行

### Requirement: 启动事件 registerTier

系统 SHALL 在游戏加载完成时投递一次 `MoreFormulaEvents.registerTier` 事件；事件内登记的门槛属于启动来源，跨 `/reload` 保留。

#### Scenario: 事件登记的门槛跨重载生效

- **WHEN** 在 `MoreFormulaEvents.registerTier(event => { event.setTier(...) })` 中登记门槛后执行 `/reload`
- **THEN** 该门槛仍然生效

### Requirement: 热重载无需重启

修改服务器脚本后执行 `/reload`，门槛变化 SHALL 立即生效（服务器来源条目在脚本重跑前被清空重建），MUST NOT 出现条目累积或「必须重启游戏才能加载新配方」的情况。

#### Scenario: 重载即生效

- **WHEN** 把某配方门槛从 `Tier.BRASS` 改为 `Tier.END` 后执行 `/reload`
- **THEN** 机器闸门与 JEI 展示均按新门槛 `Tier.END` 工作
