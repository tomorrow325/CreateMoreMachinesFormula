# Proposal

## Why

`merge-cmmm-into-cmm`（CMM 3.0 迁移）落地后的全量代码审查发现四处遗留问题，其中最严重的是：两份 KubeJS 用户文档仍停留在迁移前口径，声称「crushing/milling/cutting 需要可选依赖 CMMM、未安装时不要设门槛」——与迁移后「随 CMM 3.0 常驻可设」的实际行为相反，而 `docs/more_formula-KubeJS编写指南.md` 是 AGENTS.md 钦定的 KubeJS 行为权威说明，整合包作者按它操作会被误导去寻找一个已停发的 mod。其次是 `MoreFormula.removeTier` 与 `setTier` 不对称：传入带 `*` 的前缀 ID 会直接抛 IllegalArgumentException，且门槛表没有任何移除前缀条目的途径。

## What Changes

- **文档口径对齐 CMM 3.0**：`docs/more_formula-KubeJS编写指南.md` 与 `docs/KubeJS使用说明.md` 全面改写——crushing/milling/cutting 三类随 CMM 3.0（必需依赖）常驻可设门槛，删除全部「需要可选依赖 CMMM / 未装不要设」提法（含编写指南的排障表），改为「直接可设」。
- **README 现行行为段落顺平**：第 41、99、325 行三处「CMM/CMMM」双口径改为 CMM 单口径；历史 changelog 段落保持原样不动。
- **`removeTier` 支持前缀通配**：`MoreFormula.removeTier('create:mixing/*')` 与 `setTier` 对称——按去 `*` 归一后的前缀删除条目（跨 SERVER/STARTUP 两来源，与既有精确移除语义一致）；`RegisterTierEvent.removeTier` 同步。`Config` 新增 `removePrefixTier`。不带 `*` 的输入行为不变。
- **注释修正**：`WrenchTierAdjuster.resolveTarget` 中「找不到控制器时回退到轮子本身，避免玩家按了毫无反应」的注释与现实矛盾（轮子不在名单里，回退后必被拒绝），改为如实描述。
- **反射缓存加固**：`MekanicalCreateRecipeGate.getCandidateId` 的静态 `Method` 缓存按首个候选类定型，若将来候选混入第二种实现类会抛出未被捕获的 IllegalArgumentException——改为按候选类分桶缓存。
- **明确排除**：`SawBlockEntityMixin` 每次 `getRecipes()` 归零 `recipeIndex`（零配置时关闭锯的多配方循环）是注释里写明的既有刻意设计，不在此变更内调整；如需改动应另立变更并先做设计决策。

## Capabilities

### New Capabilities

（无 —— 全部为文档修正与小幅 API 补全。）

### Modified Capabilities

- `kubejs-api`: 「查询与移除」需求扩展——`removeTier` SHALL 支持以 `*` 结尾的前缀通配删除；其余需求（绑定、链式修饰、setTier 通配、启动事件、热重载）不变。

## Impact

- **代码**：`MoreFormulaKubeJSConfig`（removeTier 通配分支）、`RegisterTierEvent`（同）、`Config`（新增 `removePrefixTier`）、`MekanicalCreateRecipeGate`（缓存分桶）、`WrenchTierAdjuster`（仅注释）。
- **测试**：`ConfigTest` 新增通配符移除断言（移除命中/未命中/与 setPrefixTier 归一一致）。
- **文档**：`docs/` 两份指南、README 三处现行行为段落。
- **兼容性**：无破坏——`removeTier` 唯一的行为变化是「以前抛异常的输入现在正常工作」；精确移除路径不动。
