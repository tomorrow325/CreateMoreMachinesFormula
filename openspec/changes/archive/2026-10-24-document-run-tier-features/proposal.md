# Proposal

## Why

刚建立的规格基线锚定的是 b70b6e0（0.1.0）。随后从远端合入的 0.1.1/0.1.2（PR #3，至 956e728）引入了两组新的对外行为：同输入多配方「按机器等级择优」与「扳手运行等级 + 护目镜显示」，并修正了「CMMM 分级磨石」的错误口径、恢复了分级破碎轮的产量放大。这些行为目前没有规格覆盖，基线需要补齐到当前 HEAD。

## What Changes

- 新增能力 `run-tier`，覆盖扳手调整、循环规则、存储语义、可调机器名单与护目镜显示。
- `machine-gates` 新增「同输入多配方择优」相关需求（0.1.1）。
- `compat/createmoremoremachines` 修正「分级磨石」口径（CMM/CMMM 均无分级磨石，研磨由分级破碎轮控制器承载），并补充「分级破碎轮保留产量放大」需求。
- 纯文档化变更：不改任何代码；事实来源为 956e728 的实现、README 0.1.1/0.1.2 说明与扩充到 203 条的离线回归。

## Capabilities

### New Capabilities

- `run-tier`: 玩家用 Create 扳手普通右键调整机器「运行等级」（配方优先级）—— 循环与只降不升规则、准入与择优双生效面、Data Attachment 存储语义（默认未设置、不影响速度/并行、破坏即失）、7 台可调机器名单（鼓风机刻意排除）、护目镜两行显示。

### Modified Capabilities

- `machine-gates`: 新增「同输入多条不同门槛配方时按机器等级择优」需求（滤准入 + 贴近偏好档优先 + 稳定排序 + 锯 recipeIndex 归零）。
- `compat/createmoremoremachines`: 「机械锯与磨石退回」需求修正为无分级磨石口径；新增分级破碎轮在择优下保留 CMMM 产量放大。

## Impact

- 仅新增 `openspec/changes/document-run-tier-features/` 下的规格文件，归档后更新 `openspec/specs/` 的 run-tier（新建）、machine-gates、compat/createmoremoremachines 三份主规格；不改 `src/`。
- 附带事实：CMMM 依赖随远端升级到 1.21.1-1.1（类签名与 1.0 兼容），等级语义不变、无需改等级判定需求。
