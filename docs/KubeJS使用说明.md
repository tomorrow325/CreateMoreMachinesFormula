# More Formula KubeJS 使用说明书

> ⚠️ **本文已被取代**：[`more_formula-KubeJS编写指南.md`](./more_formula-KubeJS编写指南.md) 是新的权威文档，
> 内容更全、且修掉了本文的几处过时/不准确说法。**请优先阅读新指南。**
>
> 本文保留仅为兼容旧链接。已知过时之处：
> - 「JEI 显示会更新吗？会」—— 实际行为更微妙，见新指南第 10 节
> - `Tier.CREATIVE` 的说明不完整，见新指南第 8 节
> - 缺少「整条装配线共用一个门槛」这条关键规则，见新指南第 7 节
> - 缺少「启动脚本 vs 服务器脚本」的桶与 `/reload` 语义，见新指南第 9 节

> 适用于 more_formula 0.0.1+
>
> 前置：Create 6.x、Create: More Machines 2.7、KubeJS 2101.7.2+、KubeJS-Create 2101.3.1+（均为必需依赖）
>
> 注意：本模组不再使用 TOML 配置。所有门槛都通过 KubeJS 脚本设置。

---

## 支持范围（重要！先读）

本模组的门槛机制**只支持有对应分级机器的配方类型**（必需依赖 CMM 提供；可选依赖 CMMM 额外提供分级破碎轮/分级锯）。没有对应分级机器的配方**无法**通过升级机器来满足门槛，因此**不要**给这些配方类型设置门槛（否则配方永远无法完成）。

### ✅ 可以设置门槛的类型（CMM 有高级机器）

| 配方类型 | CMM 高级机器 |
|---------|-------------|
| 压机 `create:pressing` | 高级压机 |
| 混合 `create:mixing` | 高级搅拌器 + 高级盆 |
| 压实 `create:compacting` | 高级压机 + 高级盆 |
| 浇注 `create:filling` | 高级喷口 |
| 部署 `create:deploying` | 高级部署器 |
| 物品施用 `create:item_application` | 高级部署器 |
| 序列组装 `create:sequenced_assembly` | 压机 + 喷口 + 部署器 |

### ⚠️ 需要可选依赖 CMMM（未装时不要设门槛）

- 粉碎轮 `create:crushing`：分级破碎轮
- 石磨（碾磨）`create:milling`：分级破碎轮（CMMM 没有分级磨石，milling 同样由分级破碎轮控制器执行）
- 锯切 `create:cutting`：分级机械锯

安装可选依赖 **CreateMoreMoreMachines** 后，以上三类即有对应分级机器、可正常设门槛；
**未安装时不要给这些类型设门槛**，否则配方将无法被任何机器执行。

### ❌ 不要设置门槛的类型（任何环境都无高级机器）

- 喷溅 `create:splashing`
- 排空 `create:emptying`
- 流体储罐、蒸汽引擎等

---

## 等级常量

| 常量 | 值 | 含义 |
|------|-----|------|
| `Tier.ZERO` | 0 | 原版机械动力机器 |
| `Tier.BRASS` | 1 | 黄铜级 |
| `Tier.NETHERITE` | 2 | 下界合金级 |
| `Tier.END` | 3 | 末影级 |
| `Tier.BEYOND` | 4 | 超越级 |
| `Tier.CREATIVE` | -1 | 创造机器（始终允许） |

---

## 方式一：配方链式调用 `.tier()`（推荐）

直接在任意 Create 配方构建器末尾追加 `.tier(等级)`，与 `.heated()`、`.processingTime()` 等写法完全一致。
支持所有有对应分级机器的配方类型：压机 / 混合 / 压实 / 浇注 / 部署器 / 物品施用 / 序列装配。
粉碎轮、石磨、锯切在安装可选依赖 CMMM 后也可设门槛（分级破碎轮、磨石=分级破碎轮控制器、分级锯），
未安装 CMMM 时不要设；喷溅、排空等任何环境都**不支持**（见上方"支持范围"）。

脚本位置：`kubejs/server_scripts/*.js`

```js
ServerEvents.recipes(event => {
    // 压机：需要末影级（3）
    event.recipes.create.pressing('minecraft:iron_block', 'minecraft:iron_ingot').tier(3)

    // 混合 + 加热：需要下界合金级（2）
    event.recipes.create.mixing('minecraft:gold_block', '9x minecraft:gold_ingot')
        .heated()
        .tier(Tier.NETHERITE)

    // 浇注：250mB 岩浆浇到下界合金锭 → 超越级
    event.recipes.create.filling('minecraft:netherite_block', [
        'minecraft:netherite_ingot',
        Fluid.of('minecraft:lava', 250)
    ]).tier(Tier.BEYOND)

    // 部署器 + 保留手持物品
    event.recipes.create.deploying('minecraft:brass_block', [
        'minecraft:brass_ingot', 'minecraft:quartz'
    ]).keepHeldItem().tier(Tier.END)

    // 仅创造级机器：普通、高级、超越级机器都不能处理
    event.recipes.create.pressing('minecraft:nether_star', 'minecraft:diamond')
        .id('mymod:creative_only_pressing')
        .tier(Tier.CREATIVE)

    // 序列装配：整条流水线需要超越级
    let transitional = 'kubejs:incomplete_test_package'
    event.recipes.create.sequenced_assembly(
        'minecraft:copper_block',
        'minecraft:copper_ingot',
        [
            event.recipes.create.pressing(transitional, transitional),
            event.recipes.create.filling(transitional, [transitional, Fluid.of('minecraft:water', 250)]),
            event.recipes.create.deploying(transitional, [transitional, 'minecraft:quartz'])
        ]
    ).transitionalItem(transitional).loops(2).tier(Tier.BEYOND)
})
```

**原理**：通过 Mixin 注入 KubeJS 的配方对象。`.tier()` 只在脚本执行时记录门槛值并注册到 more_formula 的门槛表，不会写入配方 JSON。

`Tier.CREATIVE`（-1）是特殊门槛：只有 CMM 的创造级机器（`machineTier == -1`）允许执行；普通 Create 机器和所有非创造 CMM 机器都会被拒绝。

---

## 方式二：按配方 ID 设置门槛（事件 / 全局绑定）

适合批量管理已有配方的场景——不需要重写配方本身，直接对任意配方 ID（包括原版和其他模组的 Create 配方）设门槛。

### 2a. 事件方式

```js
MoreFormulaEvents.registerTier(event => {
    // 精确 ID
    event.setTier('create:sequenced_assembly/precision_mechanism', Tier.END)

    // 前缀通配符（以 * 结尾）：所有混合配方都要黄铜级
    event.setTier('create:mixing/*', Tier.BRASS)

    // 查询 / 删除
    event.getTier('create:mixing/brass_ingot')       // → 1
    event.removeTier('create:mixing/brass_ingot')    // 移除该条目

    event.getAllTiers()                              // 当前全部门槛 Map
})
```

### 2b. 全局绑定方式（可在任何脚本位置调用）

```js
MoreFormula.setTier('create:mixing/brass_ingot', Tier.NETHERITE)   // 设置
MoreFormula.setPrefixTier('create:filling/', Tier.END)             // 前缀批量设置
MoreFormula.getTier('create:mixing/brass_ingot')                   // 查询 → 2
MoreFormula.removeTier('create:mixing/brass_ingot')                // 删除
MoreFormula.getAllTiers()                                          // 全部条目
MoreFormula.getKnownTiers()                                        // 已用到的等级列表（JEI 分类用）
```

---

## 门槛配置

门槛只通过 KubeJS 设置，脚本位置为 `kubejs/server_scripts/*.js`。修改脚本后使用 `/reload`；修改模组后重启游戏。

```js
ServerEvents.recipes(event => {
    event.recipes.create.pressing('minecraft:iron_block', 'minecraft:iron_ingot')
        .id('mymod:iron_block')
        .tier(Tier.END)
})

// 修改已有配方
MoreFormula.setTier('create:sequenced_assembly/precision_mechanism', Tier.BEYOND)
```

---

## 常见问题

- **没装 KubeJS 或 KubeJS-Create 会怎样？** 本模组不会加载，因为两者是必需依赖。
- **`.tier()` 对哪些配方有效？** 所有有对应分级机器的类型（压机、混合、压实、浇注、部署、物品施用、序列装配；安装可选依赖 CMMM 后还支持粉碎轮、石磨、锯切）。**未安装 CMMM 时不要给粉碎轮/石磨/锯切设门槛**；喷溅、排空等任何环境都无对应高级机器，同样不要设门槛。
- **JEI 显示会更新吗？** 会。KubeJS 注册的新等级会自动出现在 JEI 分级分类标签中。
- **创造级配方如何设置？** 使用 `.tier(Tier.CREATIVE)`；只有 CMM 创造级机器允许，其他等级机器都会被拒绝。
