# Design

## Context

见 proposal.md 的 Why。当前 `openspec/specs/` 为空，而 0.1.0 的行为已稳定：核心门槛逻辑由 `src/test/java` 下的离线回归（114 条断言）覆盖，KubeJS 面与 JEI 面的行为在 `docs/more_formula-KubeJS编写指南.md` 与 README 中有用户侧描述。本变更只做规格化，不触碰代码。

## Goals / Non-Goals

**Goals:**

- 为全部对外可观察行为建立可被后续变更引用的规格基线。
- 规格只记录行为契约（输入、判定、可观察结果），不记录类名、mixin 细节等实现。

**Non-Goals:**

- 不改任何产品代码、构建脚本、依赖版本。
- 不为「实现细节级」的内容（如具体 mixin 注入点、反射桥接机制）立规格；这些留在源码注释与 BUILD.md 中。
- 不补写新的回归测试 —— 现有 ConfigTest 已覆盖规格中的门槛数据面；机器侧与 JEI 侧场景的自动化验证超出本次范围。

## Decisions

- **能力拆分为 7 个**：`tier-gating`（纯数据模型与判定）、`machine-gates`（机器侧拦截）、`kubejs-api`（脚本配置面）、`jei-integration`（展示面）、`compat/hand-made`、`compat/mekanicalcreate`、`compat/createmoremoremachines`（三个可选依赖各一）。
  理由：每份对应一条独立的演进轴 —— 门槛规则、闸门位置、脚本 API、JEI 行为、三个可选模组各自随上游版本演进。备选方案是单份大规格或按「核心 + 兼容」两份，都被否决：单份会在任何变更时被整体改写，两份会让三个上游各自不相关的变化互相污染 delta。
- `compat/` 前缀只用于可选依赖的规格；核心四能力保持扁平路径，与 `src/main/java` 下 `compat/` 包结构对应，便于定位。
- **事实来源优先级**：源码实际实现 > ConfigTest 断言 > README/BUILD 文档。文档与实现冲突时以实现为准（本次未发现冲突）。
- **已知限制写入规格而非回避**：如「已提交等级的 JEI 配方列表不做增量替换」「破碎轮不满足门槛时销毁物品（原生行为）」「未装 CMMM 时给 crushing/milling/cutting 设门槛会使配方失效」，都是用户可观察且依赖的行为，直接立为 Scenario。
- 规格中出现的标识符只限外部可见面：mod id、物品/配方 ID、KubeJS 绑定与常量、JEI 分类 ID。

## Risks / Trade-offs

- [规格与未来实现漂移] → 后续任何行为变更必须以 delta 修改对应规格；归档流程强制这一点。
- [JEI/游戏内行为难以离线验证] → 机器侧与 JEI 侧 Scenario 目前靠双场景客户端（`run/` 与 `run-noopt/`）人工验证；只有门槛数据面有离线回归兜底。
- [上游可选模组（CMM/CMMM/Mekanical-Create/Hand Made）版本升级改变行为] → 对应 `compat/*` 规格随之 delta，不影响核心四能力。
