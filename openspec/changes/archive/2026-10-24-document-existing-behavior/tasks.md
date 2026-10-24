# Tasks

## 1. 规格与实现逐项核对

- [x] 1.1 核对 `tier-gating` 规格：逐条对照 `src/main/java/org/minecart/more_formula/Config.java` 与 `util/TierHelper.java` 的优先级/分桶/幂等逻辑，并运行 `gradlew.bat check`（或先 `node tools/build.js` 再 `node tools/run_tests.js`）确认 114 条 ConfigTest 断言全绿、与规格场景一致
- [x] 1.2 核对 `machine-gates` 规格：对照 `TierHelper` 的浇注「取最严门槛」、`DeployerRecipeGate` 的事件取消与各 mixin 的拒绝路径，确认条文与实现行为一一对应
- [x] 1.3 核对 `kubejs-api` 规格：对照 `compat/kubejs/` 的绑定与事件投递时机，并与 `docs/more_formula-KubeJS编写指南.md` 交叉核对 API 名称、参数与示例行为一致
- [x] 1.4 核对 `jei-integration` 规格：对照 `compat/jei/MoreFormulaJeiPlugin.java` 的全集构建、原版分类隐藏、运行时同步与催化剂规则，确认条文（含「不重复提交」「未装 JEI 降级」）与实现一致
- [x] 1.5 核对三个 `compat/*` 规格：对照 `HandMadeRecipeGate`（空值拒绝）、`MekanicalCreateRecipeGate`（双命名空间/最低档/倍率取最高）与 `CMMMTierBridge` + 破碎轮/锯 mixin（销毁 vs 退回），确认行为描述无偏差

## 2. 校验与集成检查

- [x] 2.1 运行 `openspec validate document-existing-behavior --strict --json` 通过：Purpose 与需求描述长度、场景四号标题格式、每条需求至少一个场景
- [x] 2.2 交叉核对 proposal 的 Capabilities 列表与 `specs/` 下 7 份文件一一对应，路径与提案完全一致
- [x] 2.3 双场景客户端抽查「模组缺席不生效」类场景：`gradlew.bat runClient`（`run/`，全依赖）与 `gradlew.bat -PnoOptionalRuntime runClientNoOpt`（`run-noopt/`，无可选依赖）均能进入主菜单且日志无兼容层报错

## Workflow follow-up

- 评审通过后归档变更：`openspec archive document-existing-behavior`，归档后 `openspec/list --specs` 应出现 7 份主规格。
- 核对归档产物：`openspec list --specs` 与 `openspec show <spec-id> --type spec` 确认 Purpose 与需求完整落入主规格。
