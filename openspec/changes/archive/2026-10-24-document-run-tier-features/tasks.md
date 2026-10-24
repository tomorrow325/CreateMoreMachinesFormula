# Tasks

## 1. 规格与实现逐项核对

- [x] 1.1 核对 `run-tier` 规格：对照 `PriorityRules`（循环/可调判定/择优权重纯函数）、`MachinePriority`（存储语义）、`WrenchTierAdjuster`（扳手交互与提示）、`MachineRoster`（7 台名单与鼓风机排除）、`GoggleLines`（两行显示），确认条文一致
- [x] 1.2 核对 `machine-gates` 增量：对照 `TierHelper` 的 `getAdmissionTier`/`getSelectionTier`/`pickBest*` 与压机/工作盆/锯/磨石/破碎轮 mixin 的择优改造及 recipeIndex 归零，确认场景与实现一致
- [x] 1.3 核对 `compat/createmoremoremachines` 增量：确认「无分级磨石、研磨走破碎轮控制器」口径与 CMMM 破碎轮 mixin 注入点（保留产量放大）的实现一致

## 2. 校验与集成检查

- [x] 2.1 运行 `gradlew.bat check` 确认离线回归（203 条断言）全绿，覆盖运行等级循环/守卫、择优与名单（含鼓风机不在名单）
- [x] 2.2 运行 `openspec validate document-run-tier-features --strict --json` 通过

## Workflow follow-up

- 评审通过后同步并归档：run-tier 新建主规格；machine-gates 合并 ADDED；compat/createmoremoremachines 应用 RENAMED/MODIFIED 并直接修正主规格 Purpose 的磨石口径。
- 归档后 `openspec list --specs` 应出现 8 份主规格。
