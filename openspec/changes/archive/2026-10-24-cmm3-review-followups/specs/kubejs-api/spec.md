# Spec Delta

## MODIFIED Requirements

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
