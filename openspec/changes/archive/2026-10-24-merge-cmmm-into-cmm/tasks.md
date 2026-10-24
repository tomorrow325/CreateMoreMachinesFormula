# Tasks

## 1. 依赖切换（CMM 3.0）

- [x] 1.1 从 Y-Xiao233/CreateMoreMachines 源码构建 `createmoremachines-1.21.1-3.0.jar` 放入 `libs/`，删除 `createmoremoremachines-1.21.1-1.1.jar`；`build.gradle` 删除 CMMM fileTree/坐标段，`gradle.properties` 删除 `createmoremoremachines_version`，mods.toml 模板删除 CMMM 可选依赖条目并把 CMM 下限提升为 `[1.21.1-3.0,)`。验证：`gradlew.bat build` 通过且产物 mods.toml 不再含 createmoremoremachines
- [x] 1.2 解包 3.0 jar 核对关键类与结构：`crushing_wheel/CMMCrushingWheelControllerBlockEntity`（findRecipe 双 find 结构、getMultiple 后处理位置）、`mechanical/saw/CMMMechanicalSawBlockEntity`（是否继承 Create `SawBlockEntity`）、`BuiltInAdvancedMachineTypes` 的 `CRUSHING_WHEEL`/`SAW` 条目。验证：核对结论回填 design.md（如与源码快照有出入）

## 2. 删除 CMMM 兼容层

- [x] 2.1 删除 `compat/createmoremoremachines/` 全部 6 个文件与 `more_formula-createmoremoremachines.mixins.json`；`TierHelper` 删除 `isCMMMClassName`/`externalMachineTier`/反射桥，改为对 3.0 破碎轮（轮/控制器）与分级锯的直接 instanceof 识别（沿用 `toFormulaTier` 平移）。验证：全仓 grep 无 `createmoremoremachines`/`net.tomorrow325` 残留（迁移说明除外）

## 3. 闸门与择优重接

- [x] 3.1 按 1.2 的核对结论重做破碎轮控制器闸门：在 3.0 控制器内部 `AllRecipeTypes.find`（CRUSHING 与 MILLING 两处）做择优替换，保持 `getMultiple` 产量放大不被跳过；删除原 CMMM mixin。验证：`gradlew.bat check` 中破碎轮择优/产量放大相关断言全绿
- [x] 3.2 分级锯闸门：若 3.0 锯继承 `SawBlockEntity` 则确认现有 `SawBlockEntityMixin` 自动覆盖（含择优与 recipeIndex 归零）；否则新建 3.0 锯 mixin。验证：字节码/源码核对结论 + 相关回归断言
- [x] 3.3 `TierHelper.crushingCandidates/millingRecipes` 等选配方路径改为面向 3.0 控制器结构。验证：`gradlew.bat check` 全绿

## 4. JEI 与运行等级重接

- [x] 4.1 `MoreFormulaJeiPlugin`：删除 CMMM 桥接与在场探测，crushing/milling/cutting 机器/催化剂改经 `BuiltInAdvancedMachineTypes.CRUSHING_WHEEL`/`SAW` 解析，分级分类与序列装配分级锯上下文常驻构建。验证：JEI 相关离线断言（如分类构建门控）全绿
- [x] 4.2 `MachineRoster`/`CrushingWheelPair` 类名更新为 3.0 包（锯条目修正为 `CMMMechanicalSawBlockEntity`，名单收敛为 6 条）；鼓风机保持不入册。验证：名单相关回归断言（条目数、鼓风机排除）全绿

## 5. Mekanical-Create 命名空间收敛

- [x] 5.1 `MekanicalCreateRecipeGate` 删除 `createmoremoremachines` 命名空间分支，仅识别 `createmoremachines`。验证：模块映射相关回归断言全绿

## 6. 回归、文档与冒烟

- [x] 6.1 改写 `ConfigTest`：删除 CMMM 类名前缀断言，新增 CMM 3.0 类识别/名单 6 条/单命名空间断言，运行 `gradlew.bat check` 全绿（断言数随调整更新）
- [x] 6.2 `gradlew.bat runClient`（`run/`）与 `gradlew.bat -PnoOptionalRuntime runClientNoOpt`（`run-noopt/`）双场景冒烟：均进主菜单、日志无兼容层报错；run-noopt 场景下确认 crushing/milling/cutting 分级分类在仅 CMM 3.0 时仍构建
- [x] 6.3 更新 README（依赖表 CMM 下限 3.0、删除 CMMM 相关说明、迁移注意事项）与 AGENTS.md（libs 清单、目录结构）。验证：文档内 grep 无过时的 CMMM 依赖表述
