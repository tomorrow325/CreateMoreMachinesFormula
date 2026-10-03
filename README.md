# Create:More Machines Formula

`Create:More Machines Formula` 是一个适用于 NeoForge 1.21.1 的 Create 扩展模组，为 Create: More Machines 的高级机器增加配方等级门槛。

配方可以要求黄铜、下界合金、末影、超越或创造级机器才能处理。所有门槛都通过 KubeJS 配置，并兼容 KubeJS-Create 的 Create 配方脚本 API。

本mod由ai辅助开发

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
| Create | 6.0.0 或更高版本 |
| Create: More Machines | 2.7 或更高版本 |
| KubeJS | 2101.7.2-build.285 或更高版本 |
| KubeJS-Create | 2101.3.1-build.18 或更高版本 |

可选依赖（未安装时对应功能自动停用，不影响启动）：

| 依赖 | 版本要求 |
|------|----------|
| Create: Hand Made | 0.2.0-beta 或更高版本 |
| Mekanical-Create | 0.2.8 或更高版本（需 Mekanism） |

## 功能

- 为 Create 配方设置机器等级门槛。
- 支持辊压、搅拌、注液器、机械手、和序列装配。
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

以下类型没有对应的 CMM 高级机器，因此不建议设置门槛：

- `create:crushing`
- `create:milling`
- `create:splashing`
- `create:cutting`
- `create:emptying`
- 流体储罐和蒸汽引擎相关配方

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

## 文档与许可证

- **KubeJS 编写指南（权威，推荐先读）**：[docs/more_formula-KubeJS编写指南.md](docs/more_formula-KubeJS编写指南.md)
- KubeJS 简版说明（旧）：[docs/KubeJS使用说明.md](docs/KubeJS使用说明.md)
- 许可证：MIT，见 [LICENSE](LICENSE)
