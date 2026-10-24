# Design

## Context

见 proposal.md 的 Why。规格基线（归档于 2026-10-24-document-existing-behavior）锚定 b70b6e0；远端 956e728 带来 0.1.1（择优）与 0.1.2（运行等级/护目镜）两组行为及 CMMM 1.1 升级，本变更为其补规格，仍不触碰代码。

## Goals / Non-Goals

**Goals:**

- 把 956e728 的对外行为并入规格基线：新能力 `run-tier`，`machine-gates` 与 `compat/createmoremoremachines` 的增量。
- 修正基线中被远端 README 更正的「分级磨石」口径。

**Non-Goals:**

- 不改代码、依赖与构建。
- 不为鼓风处理配方（splashing 等）立门槛规格 —— 上游明确该能力受 API 形状限制，属未来独立功能。
- 不展开实现细节（Data Attachment 编解码、mixin 注入点选择等），只记录其可观察后果。

## Decisions

- **运行等级立为新能力 `run-tier` 而非并入 `machine-gates`**：它有独立的用户交互面（扳手/护目镜）、独立的状态存储与生命周期，且会与多个既有能力（machine-gates 准入、compat/* 机器）交叉引用；并入会让 machine-gates 在每次调整交互变化时被整体改写。
- **择优（0.1.1）写入 `machine-gates`**：它是配方选择行为的改变，与准入同属闸门语义；喷口明确排除（保持取最严门槛），写成独立场景防止误扩。
- **磨石口径用 MODIFIED 而非 ADDED**：基线「机械锯与磨石退回」需求的表述已被上游更正（无分级磨石，研磨走破碎轮控制器），必须整块改写；能力 Purpose 中的同一口径按同步规则直接编辑主规格。
- **CMMM 1.1 升级只记入 proposal 的 Impact**：类签名兼容、等级语义不变，不构成行为需求。

## Risks / Trade-offs

- [上游后续再改择优权重细节] → 择优需求只约束可观察结果（选哪条），权重数值留在实现；后续调整以 delta 修改场景即可。
- [护目镜/扳手行为难以离线验证] → 名单与循环/择优纯规则已有 203 条离线断言兜底；交互面仍需客户端人工抽查。
