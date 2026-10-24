# Tasks

## 1. `removeTier` 前缀通配支持

- [x] 1.1 `Config` 新增 `removePrefixTier(String prefix)`：按与 `addPrefixTier` 相同的归一化（去尾部 `*`、空串忽略）删除 SERVER 与 STARTUP 两来源下该前缀的条目；未命中为无害空操作。验证：`node tools/build.js` 编译通过
- [x] 1.2 `MoreFormulaKubeJSConfig.removeTier` 增加通配分支：输入以 `*` 结尾时转 `Config.removePrefixTier`，否则走既有精确路径（不再把 `*` 喂给 `ResourceLocation.parse`）；`RegisterTierEvent.removeTier` 做同样对称处理。验证：`node tools/build.js` 编译通过
- [x] 1.3 `ConfigTest` 新增断言：通配移除命中（前缀条目清空、解析回 0）、移除未登记前缀为空操作不抛异常、精确移除路径不回归。验证：`node tools/build.js` + `node tools/run_tests.js` 两步连跑全绿

## 2. 审查发现的小患修复

- [x] 2.1 `MekanicalCreateRecipeGate.getCandidateId`：静态 `Method` 缓存改为 `Map<Class<?>, Method>` 按候选类分桶，消除「首个类定型后异类 invoke 抛未捕获 IllegalArgumentException」的隐患；真正的反射失败仍按现有 IllegalStateException 语义抛出。验证：回归中 Mekanical-Create 相关断言全绿
- [x] 2.2 `WrenchTierAdjuster.resolveTarget` 注释修正为如实描述：「找不到控制器时回退到轮子本身，但轮子不在可调名单里、随后被拒绝，玩家无反馈——半结构本身无处理能力，属预期」。验证：该方法注释不再含「避免玩家按了毫无反应」的失实口径

## 3. 文档口径对齐 CMM 3.0

- [x] 3.1 `docs/more_formula-KubeJS编写指南.md`：改写「粉碎轮 / 石磨 / 锯切」整节与排障表——三类随 CMM 3.0（必需依赖）常驻可设门槛，删除「需要可选依赖 CMMM」「未安装 CMMM 时不要设」及「CMM 2.7 对 SAW 调用 withoutAll()」等迁移前论据。验证：文档 grep 无「可选依赖 CMMM」现行口径残留（历史说明除外）
- [x] 3.2 `docs/KubeJS使用说明.md`：同步「支持范围」「⚠️ 需要可选依赖 CMMM」「FAQ」三处口径为 CMM 3.0 常驻。验证：同上
- [x] 3.3 README 第 41、99、325 行三处现行行为段落的「CMM/CMMM」双口径改为 CMM 单口径；历史 changelog 与迁移注意事项段落保持原样。验证：README 中 CMMM 仅出现于 changelog/迁移说明
- [x] 3.4 终检：两步连跑回归全绿；并人工核对两份指南与 `openspec/specs/kubejs-api/spec.md` 的门槛语义口径一致（含新的 removeTier 通配语义）。验证：`node tools/build.js` + `node tools/run_tests.js` 全绿 + 口径一致
