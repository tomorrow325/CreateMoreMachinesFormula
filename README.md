# Create:More Machines Formula

`Create:More Machines Formula` 是一个适用于 NeoForge 1.21.1 的 Create 扩展模组，为 Create: More Machines 的高级机器增加配方等级门槛。

配方可以要求黄铜、下界合金、末影、超越或创造级机器才能处理。所有门槛都通过 KubeJS 配置，并兼容 KubeJS-Create 的 Create 配方脚本 API。

本mod由ai辅助开发

## 0.1.2 更新说明

0.1.1 之后的改动：

1. **新增「运行等级」：用扳手手动调整机器的配方优先级**
   手持 Create 扳手，对受支持的机器**普通右键**（不要潜行），即可把它的
   「运行等级」往下调一档：黄铜 → 无门槛 → 回到出厂等级，循环往复。
   动作栏会提示「配方优先已被调整为：X」，并播放扳手转动的音效。

   - **只能调低，不能调高**：可调范围是 `0 .. 出厂等级`，到底后回到出厂等级。
   - 调低后在两个层面同时生效：做不了高于该档的配方（准入），
     同一输入的多个配方里也优先挑贴近该档的那条（择优）。
   - **不影响处理速度与并行数**：那些读的是 CMM/CMMM 自己的出厂等级，本功能把它
     存在另一份数据里（NeoForge Data Attachment，随方块实体写进 NBT）。
   - 创造级机器也可以调（循环里额外含创造档），但**准入恒为创造级** ——
     创造机器永远什么都能做，手动值只影响它优先挑哪一档。
   - 默认不设置 = 完全跟随出厂等级，因此**旧存档行为不变**。
   - 机器被破坏后该设置随之丢失（与机器自身数据同生命周期）。

2. **戴工程师护目镜可看到这两行**
   `机械等级：X`（出厂等级）与 `运行等级：Y`（当前生效值）。
   未手动调过时两行相同，一眼能看出这台机器是否被调过。

3. **支持调整的机器（7 台）**
   CMM：机械压机、机械搅拌器、机械手（部署器）、注液器（喷口）、动力锯；
   CMMM：分级破碎轮、分级机械锯。
   破碎轮点「轮子」即可，等级写在配对的控制器上（真正跑配方的那半）。

4. **CMMM 依赖升到 1.21.1-1.1**
   1.1 的破碎轮/分级锯类签名与 1.0 一致，兼容性已用字节码核对。

> **为什么鼓风机（encased_fan）不在名单里**：CMMM 1.1 新增的分级鼓风机跑的是
> Create 的**鼓风处理**配方（`splashing` / `haunting` / `blasting` / `smoking`），
> 而本模组从未给这些配方装过门槛（`TierHelper` 只覆盖辊压、搅拌、压实、注液、
> 部署、切割、破碎、研磨）。根因在 API 形状：`FanProcessingType.canProcess(ItemStack, Level)`
> 只拿得到「物品 + 世界」，**拿不到是哪台鼓风机**，因此无法按机器等级判定。
> 给它一个按下去什么都不改变的「运行等级」等于骗人，所以先不登记 ——
> 它照常工作，只是不参与本模组的等级体系；等鼓风处理门槛做出来再一并纳入。

> 离线回归测试扩充到 203 条断言，新增覆盖运行等级的循环/守卫规则、
> 择优权重、以及「可调机器名单」本身（含鼓风机必须**不**在名单里这条）。

## 0.1.1 更新说明

0.1.0 之后的改动：

1. **同一输入存在多条不同门槛的配方时，按机器等级择优**
   修复「给下界粉碎轮喂料，出来的却是黄铜粉碎轮的产物」。
   根因不在配方顺序，而在 Create 的选配方收口点：`AllRecipeTypes.find` 走
   `RecipeManager.getRecipeFor` → `byType().filter(matches).findFirst()`，
   只取**注册顺序第一条**，与门槛无关；旧实现只在这一条上问「允许吗」，
   于是命中低级配方就放行低级配方。
   现在改为：取该输入下**所有**匹配配方 → 滤掉机器等级不够的 → 取门槛最高的一条。
   排序是稳定的，门槛相同时保持 Create 原有的相对偏好（如工作盆按配料数排序）。

   覆盖的机器：原版/分级破碎轮、原版/CMM 压机、工作盆类（搅拌、压实等）、
   机械锯、磨石。

2. **机械锯的 `recipeIndex` 归零**
   该下标随方块实体写进 NBT；列表内容与顺序已变，沿用旧存档里的下标可能取到
   不同配方。归零保证恒为「够得着的最高等级」那条。（无功能副作用。）

3. **修复 CMMM 分级破碎轮丢失产量放大**
   上一版在 `findRecipe` 的 RETURN 处整体替换返回值，会跳过 CMMM 按等级放大产量的
   后处理（倍率 > 1 时把配方包一层、放大产出并缩短时长），使分级破碎轮失去
   「等级越高产得越多」。改为在 CMMM 内部那次 `find` 处注入，只替换「它找到哪条」，
   放大逻辑照常生效。

4. **磨石准入判定改为按最高门槛**
   `canProcess` 只管「准不准进」，不决定进去后做哪条；因此按该输入所有匹配配方里的
   最高门槛判定（宁严勿漏）。本模组与 CMM/CMMM 均无分级磨石，故磨石恒为 0 级、
   带门槛配方一律拒绝，与旧行为一致。

5. **手工构建链补上 `-g`**
   `tools/genargs.js` 原先未给 javac 传 `-g`，产物缺 `LocalVariableTable`，
   玩家崩溃日志里局部变量名会丢（jar 也小约 6%）。已对齐 Gradle 默认的调试信息。

> 离线回归测试扩充到 145 条断言，其中包含直接复现上述场景的用例
> （同输入 t1/t2：二级机器选 t2、一级机器退到 t1、零级机器两条都不够）。

## 0.1.0 更新说明

0.0.3b 之后的改动：

1. **可选接入 CreateMoreMoreMachines（1.21.1-1.0+）**
   分级破碎轮与分级机械锯纳入等级门槛：通过 Mixin 拦截其配方处理逻辑，
   按机器等级换算门槛判定。破碎轮对不满足门槛的物品保持原生表现（销毁），
   机械锯不满足门槛时按「无匹配配方」处理、物品原样退回。

   （口径修正：CMMM 1.0 只发布了分级破碎轮与分级锯，**没有分级磨石** ——
   其 jar 内 `api/content/` 下仅有 `crushing_wheel` 与 `mechanical_saw` 两组。
   本模组另有原版磨石的闸门，但那台机器恒为 0 级。CMMM 的分级破碎轮控制器
   自带 milling 回退查找，因此「设了门槛的研磨配方」也会被它的门槛拦住。）

2. **CMMM 的 JEI 分级分类与动画**
   装有 CMMM 时，设了门槛的破碎/研磨/锯切配方进入 more_formula 的分级分类
   （带分级破碎轮、分级锯动画渲染），并从 Create 原版对应分类隐藏；
   序列装配的锯切工序也改用分级锯渲染，分级锯同时作为序列装配的催化剂显示。
   未装 CMMM 时 JEI 行为与 0.0.3b 完全一致。

3. **Mekanical-Create 联动扩展**
   识别 CMM/CMMM 双命名空间的模块：CMMM 的分级破碎轮与分级锯作为模块时
   同样按档享受速度/并行倍率，倍率可在服务器配置中按机器类型调整。

4. **稳定性与测试**
   Hand Made 共享闸门空值面按「拒绝」处理；离线回归测试扩充到 114 条断言，
   覆盖 CMMM 类名前缀判定、双命名空间模块映射与 mixin 配置插件门控。

## 0.0.3b 更新说明

0.0.2 之后的改动：

1. **可选接入 Mekanical-Create（0.2.8+）**
   装有该模组时，CMM 模块会为 Mekanical-Create 的机器提供速度/并行加成：
   模拟腔（Simulation Chamber）的并行 lane 数按模块倍率放大；
   工厂多方块（Factory）每 tick 的 work budget 按最高启用催化剂的倍率放大，
   单条配方的总能耗不重复放大；多催化剂同时生效时取最高倍率而非相乘。
   倍率可在服务器配置中按 4 类机器（压机/部署器/搅拌器/喷口）× 5 个 CMM 等级调整，
   范围 1–64，默认 1/2/3/4/4。

2. **Mekanical-Create 的 JEI 显示按「最低适用机器」过滤**
   每个配方在 JEI 里只显示刚好满足其门槛的最低档机器，创造级门槛只匹配创造模块，
   高级模块不再把低门槛配方重复刷屏；
   同时修复了 JEI 双重派生 ID（模块行内再嵌套一层）无法还原原配方 ID 的问题。

3. **可选接入 Create: Hand Made（0.2.0-beta+）**
   手工工具（手锯、研钵、压锤、灌注枪、指杆、搅拌棒、风箱）只能处理未设门槛（Tier.ZERO）的配方，
   防止用手工方式绕过机器等级门槛。

4. **统一换行符**
   新增 `.gitattributes` 强制文本文件使用 LF（`.bat`/`.cmd` 保持 CRLF），
   保证在不同系统上 clone 后构建出的 jar 内容逐字节一致。

5. **项目更名为 CreateMoreMachinesFormula**
   构建产物 JAR 名称与游戏内显示的模组名称更名为 `CreateMoreMachinesFormula`；
   `mod_id` 仍为 `more_formula`，命名空间与脚本 API 不变，现有 KubeJS 脚本与配置无需改动。

两个可选依赖在未安装时自动停用，不影响启动。

## 0.0.2 修复说明

0.0.1 存在下列问题，0.0.2 已全部修复：

1. **`/reload` 后门槛表无限膨胀（原「必须大退游戏」的根因）**
   门槛表此前没有任何清理逻辑，而 `/reload` 会重跑服务器脚本、再次写入门槛，
   前缀条目还会重复追加，越积越多。现在按来源分桶：
   服务器脚本写的条目在每次重载前清空、启动脚本写的条目保留，
   同一个前缀重复注册也变成幂等替换。
2. **用前缀门槛配的配方在 JEI 里彻底消失**
   隐藏逻辑用的是 `getRequiredTier()`（能解析前缀），而建分级分类用的是
   `getKnownTiers()`（当时只统计精确条目）。于是前缀门槛命中的配方
   既被从原版 Create 分类里隐藏掉、又没有对应的分级分类可去 —— 在 JEI 里完全看不到。
   现在 `getKnownTiers()` 精确 + 前缀都计入。
3. **JEI 分级分类的构建时机依赖脚本执行顺序**
   分类改为按等级全集构建，运行时再按当前门槛表隐藏/取消隐藏并补交配方，
   不再受「JEI 建分类时脚本是否已经跑过」的影响。
4. **移除了锯（sawing/cutting）相关的死代码**
   CMM 2.7 通过 `withoutAll()` 关掉了 SAW，一个分级锯方块都没有注册，
   原代码却仍注册了锯的 Mixin 闸门、分级 JEI 分类和分级锯动画（永不生效）。
   现在锯切若出现在序列装配里，直接使用 Create 原版的锯动画。
5. **浇注配方的门槛判定不再受配方顺序影响**
   原实现「取第一条匹配到的浇注配方」的门槛，而 Create 实际执行的是
   `RecipeManager` 返回顺序里的第一条，两者可能不是同一条 → 可能漏放。
   现在对所有候选取最严门槛。
6. **合并了重复的门槛存储**
   原来精确门槛同时写进「内建」和「KubeJS」两张表（内容永远相同），
   前缀也一样，优先级分层形同虚设。现改为按来源分桶的单一结构，
   优先级规则明确：精确 > 前缀，同类型下服务器脚本 > 启动脚本，前缀取最长匹配。

## 依赖

以下依赖**全部**必需：

| 依赖 | 版本要求 |
|------|----------|
| Minecraft | 1.21.1 |
| NeoForge | 21.x |
| Create | 6.0.10 或更高版本 |
| Create: More Machines | 2.7 或更高版本 |
| KubeJS | 2101.7.2-build.285 或更高版本 |
| KubeJS-Create | 2101.3.1-build.18 或更高版本 |

可选依赖（未安装时对应兼容完全不生效，也不影响启动）：

| 模组 | modId | 版本要求 | 作用 |
|------|-------|----------|------|
| Create: Hand Made | `create_hand_made` | 0.2.0-beta 或更高 | 手工机器只允许处理 `Tier.ZERO` 配方 |
| Mekanical-Create | `mekanicalcreate` | 0.2.8 或更高（需 Mekanism） | 按 CMM 模块等级提供速度/并行加成（服务器 TOML 配置可调） |
| CreateMoreMoreMachines | `createmoremoremachines` | 1.21.1-1.1 或更高 | 分级破碎轮/机械锯纳入等级门槛（见下方配方类型说明） |

## 功能

- 为 Create 配方设置机器等级门槛。
- 支持辊压、搅拌、注液器、机械手、和序列装配。
- 手持 Create 扳手**普通右键**受支持的机器，可手动调低它的「运行等级」（配方优先级），
  动作栏会提示调整结果；戴工程师护目镜可在悬浮信息栏看到 `机械等级` 与 `运行等级` 两行。
  只能调低不能调高，且不影响处理速度与并行数。详见上方 0.1.2 更新说明。
- 为 JEI 注册按等级区分的配方分类标签。
- JEI 中显示对应等级的 CMM **高级机器动画**和催化剂。
- 支持创造级专属配方。
- 支持按配方 ID 精确设置门槛和按前缀批量设置门槛。
- 不使用 TOML 配置文件。

## 等级

| 常量 | 数值 | 说明 |
|------|------|------|
| `Tier.ZERO` | `0` | 无门槛，原版 Create 机器即可处理 |
| `Tier.BRASS` | `1` | 黄铜级 |
| `Tier.NETHERITE` | `2` | 下界合金级 |
| `Tier.END` | `3` | 末影级 |
| `Tier.BEYOND` | `4` | 超越级 |
| `Tier.CREATIVE` | `-1` | 仅 CMM 创造级机器 |

普通等级遵循“当前机器等级大于等于配方门槛”的规则。创造级门槛是特殊规则：只有机器等级为 `-1` 的创造机器可以处理，其他所有机器都会被拒绝。

## 安装

1. 安装 NeoForge 1.21.1。
2. 安装 Create、Create: More Machines、KubeJS 和 KubeJS-Create。
3. 将 `CreateMoreMachinesFormula` 的 JAR 文件放入游戏的 `mods` 文件夹。
4. 启动游戏。

## KubeJS 用法

在 `kubejs/server_scripts/` 下创建脚本：

```kjs
ServerEvents.recipes(event => {
    // 需要末影级压机
    event.recipes.create.pressing(
        'minecraft:iron_block',   //产物
        'minecraft:iron_ingot'    //反应物
    ).id('example:end_pressing')  //配方id
        .tier(Tier.END)           //机器等级

    // 需要下界合金级，并且必须加热
    event.recipes.create.mixing(
        'minecraft:gold_block',
        '9x minecraft:gold_ingot'
    ).heated()
        .id('example:heated_mixing')
        .tier(Tier.NETHERITE)

    // 只有创造级机器可以处理
    event.recipes.create.pressing(
        'minecraft:beacon',
        'minecraft:nether_star'
    ).id('example:creative_pressing')
        .tier(Tier.CREATIVE)
})
```
以及装配线
```kjs
………
    // 序列装配：整条装配线需要超越级
    let transitional = 'kubejs:incomplete_test_package'  //装配线名字
    event.recipes.create.sequenced_assembly(
        'minecraft:copper_block',  //产物
        'minecraft:copper_ingot',  //反应物
        [
            event.recipes.create.pressing(transitional, transitional),   //第一道工序，辊压
            event.recipes.create.filling(transitional, [transitional, Fluid.of('minecraft:water', 250)]),  //第二道工序，注液
            event.recipes.create.deploying(transitional, [transitional, 'minecraft:quartz'])  //第三道工序，机械手装配
        ]
    ).transitionalItem(transitional).loops(2).tier(Tier.BEYOND)
}
```
#### [更多kjs使用说明点我](./docs/KubeJS使用说明.md)

### 修改脚本后在游戏中执行：

```指令
/reload
```

## 支持的配方类型

可以设置门槛的类型：

- `create:pressing`：动力辊压机
- `create:mixing`：动力搅拌器和工作盆
- `create:compacting`：动力辊压机和工作盆
- `create:filling`：注液器
- `create:deploying`：机械手
- `create:item_application`：拿物品的机械手
- `create:sequenced_assembly`：序列装配

以下类型在**原版 CMM** 里没有对应高级机器：

- `create:crushing` / `create:milling`：破碎轮 / 磨石
- `create:cutting`（含切石）：机械锯
- `create:splashing`：鼓风嘴
- `create:emptying`
- 流体储罐和蒸汽引擎相关配方

其中破碎、研磨、锯切三类在安装可选依赖 **CreateMoreMoreMachines** 后即有对应分级机器
（分级破碎轮/磨石、分级机械锯），可以正常设置门槛；**未安装时不要给这些类型设门槛**，
否则配方将无法被任何机器执行。另有两点机器原生行为需要注意：

- 分级破碎轮对"等级不够"的配方按原版「无配方」处理：物品在倒计时结束后被研磨掉
  （破碎轮对不可研磨物品的原生表现就是销毁，不存在"原样退回"）；
- 分级机械锯与磨石则是无损的：不满足门槛的配方按"无匹配配方"处理，物品原样退回。

装了 CMMM 后，JEI 里设了门槛的 crushing/milling/cutting 配方会从原版 create 分类
（锯切的原版分类是 create:sawing）隐藏、进入 more_formula 的分级分类，序列装配的
锯切工序也渲染分级锯；**未装 CMMM 时 JEI 行为与旧版完全一致**（不建新分类、不隐藏）。

## 按 ID 设置门槛

不需要重写原配方，也可以直接绑定门槛：

```kjs
// 精确设置
MoreFormula.setTier(
    'create:sequenced_assembly/precision_mechanism',   //配方id
    Tier.END                                           //设置等级
)

// 为指定前缀的配方设置门槛
MoreFormula.setPrefixTier('create:mixing/', Tier.BRASS)

// 查询和移除
MoreFormula.getTier('create:mixing/brass_ingot')
MoreFormula.removeTier('create:mixing/brass_ingot')
```

也可以使用事件方式批量管理：

```kjs
MoreFormulaEvents.registerTier(event => {
    event.setTier('create:mixing/*', Tier.BRASS)
    event.setTier(
        'create:sequenced_assembly/precision_mechanism',
        Tier.BEYOND
    )
})
```

## 构建

标准方式（需要能访问 `maven.neoforged.net` 等仓库），Java 21：

```bash
./gradlew build
```

Windows：

```bat
gradlew.bat build
```

构建产物位于 `build/libs/CreateMoreMachinesFormula-版本号.jar`。

### 免 Gradle 构建（仓库不可达时）

`tools/` 下有一套手工 javac 链：复用 Gradle 缓存里已解好的 MC+NeoForge 合并包，
不经过 Gradle 解析。Windows 上直接跑：

```bat
build_run.cmd
```

它会依次执行 `tools/genargs.js`（生成 @argfile）→ `javac` → `tools/pack.js`（打包）。
测试用 `node tools/run_tests.js`。

注意 `tools/genargs.js` 里的 `-g` 不能去掉：Gradle 的 `JavaCompile` 默认带全套调试信息，
而裸 javac 默认**不含局部变量表**；少了它，玩家提交的崩溃日志会丢局部变量名（jar 也小约 6%）。

## 文档与许可证

- **KubeJS 编写指南（权威，推荐先读）**：[docs/more_formula-KubeJS编写指南.md](docs/more_formula-KubeJS编写指南.md)
- KubeJS 简版说明（旧）：[docs/KubeJS使用说明.md](docs/KubeJS使用说明.md)
- 许可证：MIT，见 [LICENSE](LICENSE)
