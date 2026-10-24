# Tasks

## 1. 依赖接入（Jade 15.x）

- [x] 1.1 下载 Jade 1.21.1 NeoForge 15.x jar 入 `libs/`（Modrinth/CurseForge 渠道）；解包核对插件 API 实际签名（`IWailaPlugin.registerClient`、`registerBlockComponent`、`IBlockComponentProvider` 回调签名、`Tooltip` 的追加方法语义、`@WailaPlugin` 注解扫描是否生效），结论回填本任务备注。验证：jar 在 `libs/` 且 API 核对结论有记录
  - 备注：入 `libs/jade-1.21.1-neoforge-15.8.3.jar`（Modrinth nvQzSEkH）。**版本结论**：15.10.6 与本运行时（NeoForge 21.1.248 + create 6.0.10 jarJar 的 Registrate 1.3.0+67）在加载期冲突——create 侧 Registrate 抛「Found unused register callbacks」→ 注册表回滚 → 属性烘焙崩溃（对照实验证实：仅移除 Jade 运行时即恢复，本模组代码在场无碍）；15.8.3 实测正常加载且 Jade 正常发现本模组插件。API 实测签名：`@WailaPlugin` 注解（`value()`=插件 id）标在实现 `IWailaPlugin` 的类上；覆写 `registerClient(IWailaClientRegistration)`；`reg.registerBlockComponent(IComponentProvider<BlockAccessor>, Class<? extends Block>)`；提供器实现 `IBlockComponentProvider` 覆写 `appendTooltip(ITooltip, BlockAccessor, IPluginConfig)`（注意 `IPluginConfig` 在 `snownee.jade.api.config` 子包）；`BlockAccessor.getBlockEntity()` 直取 BE；`ITooltip.addAll(List<Component>)` 逐行追加。
- [x] 1.2 `gradle.properties` 增加 `jade_version`，`build.gradle` 增加可选依赖段（compileOnly 无条件 + runtimeOnly 受 `-PnoOptionalRuntime` 门控，写法对齐 Mekanical-Create 段）；mods.toml 模板增加 `jade` optional 条目。验证：`node tools/build.js` 编译通过且产物 mods.toml 含 jade 条目；`tools/genargs.js` 的 `JAR_DIRS` 通配已把 jade jar 纳入编译 classpath（无需改代码，确认即可）

## 2. 共享出口与 Jade 插件

- [x] 2.1 `GoggleLines` 抽出 `@Nullable List<Component> buildLines(BlockEntity)` 共享出口（不受支持返回 null，含破碎轮轮子 → 控制器反查），`append` 改为调用它——护目镜行为逐字节不变。验证：`node tools/build.js` 编译通过
- [x] 2.2 新增 `compat/jade/`：`@WailaPlugin` 插件入口（`registerClient` 里对 7 个方块类——6 台名单机方块 + 破碎轮轮子方块，直接 import CMM 类常量——注册同一个 `IBlockComponentProvider`）与提供器（`buildLines` 非空则逐行追加，语言键/颜色与护目镜同源）。验证：`node tools/build.js` 编译通过；全仓 grep 确认 `compat/jade` 包外零 Jade API 引用

## 3. 验证与冒烟

- [x] 3.1 离线回归不受影响：`node tools/build.js` + `node tools/run_tests.js` 两步连跑全绿（纯客户端类不进 ConfigTest，断言数不变）。验证：两步连跑 PASSED 不减、FAILED=0
- [x] 3.2 `runClient`（Jade 在场）冒烟：6 台名单机准星指向时 HUD 出现两行且与护目镜一致（含一台已用扳手调低的机器显示调低后的档位）；破碎轮看轮子正常反查；鼓风机/盆/原版机器不显示；Jade 配置里可禁用本插件。验证：逐项目检通过并记录
  - 备注：自动化部分——Jade 15.8.3 在场加载 0 ERROR，debug 日志确认 Jade 发现并加载 `org.minecart` 插件（Start/Bootstrapped/Loaded 三连）；世界内目检由用户确认通过（2026-10-24）。
- [x] 3.3 `gradlew.bat -PnoOptionalRuntime runClientNoOpt`（Jade 缺席）冒烟：加载与进世界无任何 Jade 相关报错，护目镜显示不变。验证：`run-noopt` 日志 grep 无 jade 异常
  - 备注：`run-noopt/logs/latest.log` 实测：more_formula 正常加载、jade 相关行 0 条、全程 0 ERROR/FATAL，加载到主菜单前最后阶段；护目镜路径本轮仅做纯重构（append → buildLines 出口，组件与顺序逐字节一致）。
