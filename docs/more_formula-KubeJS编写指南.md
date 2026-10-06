# more_formula KubeJS 编写指南

> 适用版本：**more_formula 0.0.2+**
> 前置依赖（全部必需）：Minecraft 1.21.1 · NeoForge 21.x · Create 6.x · Create: More Machines 2.7+ · KubeJS 2101.7.2+ · KubeJS-Create 2101.3.1+
>
> 本文是本模组 KubeJS 用法的**权威参考**。与旧的《KubeJS使用说明.md》冲突时以本文为准。

---

## 目录

1. [门槛是什么](#1-门槛是什么)
2. [30 秒快速开始](#2-30-秒快速开始)
3. [等级表](#3-等级表)
4. [最重要的两条规则](#4-最重要的两条规则)
5. [四种设置门槛的方式](#5-四种设置门槛的方式)
6. [支持的配方类型全表](#6-支持的配方类型全表)
7. [装配线专题（重点）](#7-装配线专题重点)
8. [创作级门槛](#8-创作级门槛)
9. [脚本放哪里 / `/reload` 行为](#9-脚本放哪里--reload-行为)
10. [排错与自检](#10-排错与自检)
11. [常见错误清单](#11-常见错误清单)
12. [完整可跑示例](#12-完整可跑示例)

---

## 1. 门槛是什么

Create 的配方本身**不知道机器有等级** —— 一个 `create:pressing` 配方，在普通压机和超越级压机上都能做。

more_formula 给它加一道「**机器等级门槛**」：只有等级足够的 CMM 高级机器才允许处理这条配方，等级不够时**机器完全不执行这道工序**（物品原地不动、不消耗、不推进）。

判定规则（来自源码 `TierHelper.isTierAllowed`）：

| 配方门槛 | 机器等级 | 结果 |
|---|---|---|
| `0`（不设门槛） | 任意 | ✅ 允许 |
| `1`~`4` | 同等级或更高 | ✅ 允许 |
| `1`~`4` | 低于门槛 | ❌ 拒绝 |
| `1`~`4` | 创造级机器（`-1`） | ✅ 允许（创造级可以干任何活） |
| `-1`（创作级） | 只有创造级机器（`-1`） | ✅ 允许 |
| `-1`（创作级） | 其他任何等级 | ❌ 拒绝 |

> 注意：**原版 Create 机器的等级视为 0**。所以只要你给配方设了 `1` 以上的门槛，原版机器就做不了了 —— 这正是本模组的用途。

---

## 2. 30 秒快速开始

在 `kubejs/server_scripts/` 下新建任意 `.js` 文件：

```js
ServerEvents.recipes(event => {
    // 这个压机配方需要「末影级」压机
    event.recipes.create.pressing(
        'minecraft:iron_block',      // 产物
        'minecraft:iron_ingot'       // 输入
    ).tier(Tier.END)                 // ← 加这一行就是门槛
})
```

存盘 → 游戏里 `/reload` → 完成。

`.tier()` 的用法和 `.heated()`、`.processingTime()` 完全一样，是对 Create 配方构建器再链一个调用。

---

## 3. 等级表

脚本里直接用 `Tier` 常量（这是模组注册的全局绑定），比写数字可读：

| 常量 | 值 | 含义 | 对应的 CMM 机器前缀 |
|---|---|---|---|
| `Tier.ZERO` | 0 | 不设门槛（原版机器即可） | — |
| `Tier.BRASS` | 1 | 黄铜级 | `brass_*` |
| `Tier.NETHERITE` | 2 | 下界合金级 | `netherite_*` |
| `Tier.END` | 3 | 末影级 | `end_*` |
| `Tier.BEYOND` | 4 | 超越级 | `beyond_*` |
| `Tier.CREATIVE` | -1 | 仅创造级机器 | `creative_*` |

直接写数字也可以（`.tier(3)` 等价于 `.tier(Tier.END)`）。

> 内部换算说明（不用记）：CMM 自己的等级值是 brass=2 / netherite=3 / end=4 / beyond=5 / creative=-1，本模组统一平移成 1~4 和 -1。你只需要用上面的常量。

---

## 4. 最重要的两条规则

### 规则一：整条装配线共享一个门槛

Create 跑序列装配的某道工序时，交给机器的**「配方 ID」是整条装配线（外层配方）的 ID**，不是工序自己的 ID。它内部是：

```java
new RecipeHolder(外层装配线.id, 该工序的配方)   // ← 用的是外层的 id
```

这一点已用字节码核对 `SequencedAssemblyRecipe#getRecipe` / `#getRecipes` 确认。

**结论：做不到「第 1 道工序要黄铜级、第 2 道工序要末影级」。** 要覆盖多个等级，就写多条线。

### 规则二：不要给「没有高级机器」的类型设门槛

CMM 只给一部分机器做了高级版。给没有高级版的类型设门槛，等于**这条配方永远做不出来**。

详见下一节的表。

---

## 5. 四种设置门槛的方式

### 方式 A：`.tier()` 链式（推荐，默认用这个）

在配方构建器末尾追加，和 `.heated()` 并列：

```js
ServerEvents.recipes(event => {
    event.recipes.create.pressing('minecraft:iron_block', 'minecraft:iron_ingot')
        .tier(Tier.END)

    event.recipes.create.mixing('minecraft:gold_block', '9x minecraft:gold_ingot')
        .heated()                    // 原有方法照用
        .tier(Tier.NETHERITE)

    event.recipes.create.deploying('minecraft:brass_block', [
        'minecraft:brass_ingot', 'minecraft:quartz'
    ]).keepHeldItem()
        .tier(Tier.END)
})
```

**位置**：`kubejs/server_scripts/*.js`

**注意**：`.tier()` 只把门槛记进本模组的门槛表，**不会写进配方 JSON**。所以你在配方 json / JEI 里看不到门槛字段，这是正常的。

---

### 方式 B：按配方 ID 设置（改已有配方，不重写配方）

适合「原版/其他模组的配方，我不想重写，只想加个门槛」：

```js
// 精确 ID
MoreFormula.setTier('create:sequenced_assembly/precision_mechanism', Tier.END)

// 查询（返回该配方当前生效的门槛，0 = 无门槛）
console.info('门槛 = ' + MoreFormula.getTier('create:sequenced_assembly/precision_mechanism'))

// 删除
MoreFormula.removeTier('create:sequenced_assembly/precision_mechanism')
```

**位置**：`kubejs/server_scripts/*.js`

---

### 方式 C：前缀批量设置

以 `/` 结尾（或 `*` 结尾，两种等价）的前缀，会命中该前缀下的**所有**配方：

```js
// 所有 create:mixing/ 开头的配方都要黄铜级
MoreFormula.setPrefixTier('create:mixing/', Tier.BRASS)

// 等价写法（* 会被自动去掉）
MoreFormula.setTier('create:mixing/*', Tier.BRASS)
```

匹配规则（`Config.getRequiredTier`）：

- **精确命中优先于前缀命中**
- 多个前缀命中时，**最长前缀优先**（`create:mixing/brass` 比 `create:mixing/` 更具体）
- 同类型下，`server_scripts` 里设的优先于 `registerTier` 事件设的

> ⚠️ 前缀会影响该前缀下的**全部**配方，包括你没意识到的那些。**调试阶段建议先用精确 ID**，确认无误后再换前缀批量。

---

### 方式 D：`registerTier` 启动事件

```js
MoreFormulaEvents.registerTier(event => {
    event.setTier('create:mixing/*', Tier.BRASS)                       // 支持通配
    event.setTier('create:sequenced_assembly/precision_mechanism', Tier.BEYOND)
    event.getTier('create:mixing/brass_ingot')                          // 查询
    event.removeTier('create:mixing/brass_ingot')                       // 删除
    event.getAllTiers()                                                // 当前全部
})
```

**位置**：`kubejs/startup_scripts/*.js`（这是 startup 类型事件）

**与方式 B/C 的关键区别**（读源码确认，见 [第 9 节](#9-脚本放哪里--reload-行为)）：

| | `.tier()` / `MoreFormula.setTier` | `MoreFormulaEvents.registerTier` |
|---|---|---|
| 注册来源 | 服务器脚本桶 | 启动脚本桶 |
| `/reload` 时 | 清空后由脚本重新写入 | **保留**（不重跑） |
| 改动后生效 | `/reload` 立即生效 | 需**重启游戏** |

简单说：**要 `/reload` 能改的，用方式 A/B/C 写在 `server_scripts`；想「一次设定、永不随重载变动」的，用方式 D 写在 `startup_scripts`。**

---

## 6. 支持的配方类型全表

### ✅ 可以设门槛（CMM 有对应高级机器）

| 配方类型 | KubeJS 写法 | 需要的 CMM 高级机器 | 闸门实现在哪 |
|---|---|---|---|
| `create:pressing` | `event.recipes.create.pressing(...)` | 高级压机 | 压机的 `getRecipe` |
| `create:mixing` | `event.recipes.create.mixing(...)` | 高级搅拌器 + 高级盆 | 盆基类的 `getMatchingRecipes` |
| `create:compacting` | `event.recipes.create.compacting(...)` | 高级压机 + 高级盆 | 盆基类的 `getMatchingRecipes` |
| `create:filling` | `event.recipes.create.filling(...)` | 高级喷口 | 喷口的 `onItemReceived` / `whenItemHeld` |
| `create:deploying` | `event.recipes.create.deploying(...)` | 高级部署器 | 部署器配方搜索事件 |
| `create:item_application` | `event.recipes.create.item_application(...)` | 高级部署器 | 部署器配方搜索事件 |
| `create:sequenced_assembly` | `event.recipes.create.sequenced_assembly(...)` | 取决于各工序用到的机器 | 见 [第 7 节](#7-装配线专题重点) |

### ⚠️ 粉碎轮 / 石磨 / 锯切：需要可选依赖 CMMM

`create:crushing`（粉碎轮）、`create:milling`（石磨／碾磨）、`create:cutting`（锯切）
在安装可选依赖 **CreateMoreMoreMachines（CMMM）** 后即有对应分级机器
（五档分级破碎轮、分级机械锯；CMMM 没有分级磨石，milling 同样由分级破碎轮控制器执行），
可以正常设门槛。**未安装 CMMM 时给这些设门槛 → 配方永远无法完成**，不要设。

> 关于锯切：CMM 2.7 本体**完全没有发布任何高级锯** —— 它在自己的 tier 插件里对 `SAW` 直接调用了 `withoutAll()`（已用字节码确认），jar 内也没有任何 saw 的模型/贴图。可选依赖 CMMM 补上了五档分级锯与分级破碎轮；只装 CMM 2.7 时这三类仍然没有高级版。

### ❌ 不要设门槛（任何环境都没有对应高级机器）

给这些设门槛 → **配方永远无法完成**：

- `create:splashing`（喷溅）
- `create:emptying`（排空）
- 流体储罐、蒸汽引擎相关配方

---

## 7. 装配线专题（重点）

### 7.1 整条线共用一个门槛

```js
ServerEvents.recipes(event => {
    const T = 'kubejs:incomplete_package'      // 中间产物（自己起名）

    event.recipes.create.sequenced_assembly(
        'minecraft:copper_block',               // 最终产物
        'minecraft:copper_ingot',               // 起始输入
        [
            event.recipes.create.pressing(T, T),                                        // 工序1 辊压
            event.recipes.create.filling(T, [T, Fluid.of('minecraft:water', 250)]),      // 工序2 注液
            event.recipes.create.deploying(T, [T, 'minecraft:quartz'])                   // 工序3 机械手
        ]
    )
        .transitionalItem(T)
        .loops(2)
        .tier(Tier.BEYOND)                      // ← 三道工序全部要求超越级
})
```

上面这条线，**压机、喷口、部署器三者都必须是超越级**才行。任何一个低于超越级，整条线就卡在那道工序。

如果你需要多种等级，就**写多条线、每条线一个等级**（因为一条线只能有一个等级）：

```js
// 线1：黄铜级，只要一台黄铜压机
event.recipes.create.sequenced_assembly('minecraft:iron_block', 'minecraft:redstone', [
    event.recipes.create.pressing(T, T)
]).transitionalItem(T).loops(1).id('mypack:a_brass').tier(Tier.BRASS)

// 线2：下界合金级，只要一台下界合金喷口
event.recipes.create.sequenced_assembly('minecraft:gold_block', 'create:zinc_ingot', [
    event.recipes.create.filling(T, [T, Fluid.of('minecraft:water', 250)])
]).transitionalItem(T).loops(1).id('mypack:a_netherite').tier(Tier.NETHERITE)

// 线3：末影级，只要一台末影部署器
event.recipes.create.sequenced_assembly('minecraft:emerald_block', 'minecraft:quartz', [
    event.recipes.create.deploying(T, [T, 'minecraft:quartz'])
]).transitionalItem(T).loops(1).id('mypack:a_end').tier(Tier.END)

// 线4：超越级，三种机器都要超越级
event.recipes.create.sequenced_assembly('minecraft:netherite_block', 'minecraft:amethyst_shard', [
    event.recipes.create.pressing(T, T),
    event.recipes.create.filling(T, [T, Fluid.of('minecraft:lava', 250)]),
    event.recipes.create.deploying(T, [T, 'create:cogwheel'])
]).transitionalItem(T).loops(1).id('mypack:a_beyond').tier(Tier.BEYOND)
```

> ⚠️ 上面四条线里的**起始输入物必须互不相同**，也不能和整合包里已有的装配线撞车，见 7.3。

**一条线的现成模板**（改一个常量就能切等级）见游戏内
`kubejs/server_scripts/more_formula_assembly_test.js`。

### 7.2 中间产物怎么选

- 需要一个**专属的中间产物**（`transitionalItem`）。写成物品 ID 字符串即可，KubeJS 会自己处理。
- 可以自己注册（`StartupEvents.registry('item', ...)`），也可以复用 `more_formula:incomplete_test_package` 或 Create 自带的 `create:incomplete_precision_mechanism` 等。
- **多条线复用同一个中间产物是安全的**：Create 会把「装配线 ID」写进该物品的数据组件来区分归属，各条线不会串味。真正需要彼此区分的是**起始输入物**。

### 7.3 ⚠️ 起始输入物不能和别人撞车

Create 是**靠起始输入物找装配线的**（`SequencedAssemblyRecipe#appliesTo` 用 `Ingredient.test` 匹配输入）。如果两条线的输入物相同：

- 两条线会同时命中，**结果取决于配方注册顺序** —— 可能跑的不是你想要的那条。
- 你甚至可能「无意中」和某个模组已有的装配线撞车。

**动手前先查一下这个整合包里已经有哪些装配线。** 本整合包当前有 35 条（跨 8 个模组 + KubeJS 脚本），已占用的**输入物**包括：

```
ae2:molecular_assembler              appliedcreate:stress_storage_cell_256m
create:andesite_alloy                create:golden_sheet
create:iron_sheet                    createpackage:basic_package_distributor
createpackage:kinetic_pattern_provider  createpackage:package_distributor
dreammod:dream_nodule                dreammod:pure_dream
laowu:cat_can                        laowu:cat_component
laowu:cat_shell                      laowu:intermediate_breeding_box
minecraft:leather                    minecraft:string
minecraft:copper_ingot               （来自 kubejs/server_scripts/create.js）
TAG:c:dusts/obsidian  TAG:c:plates/brass  TAG:c:plates/copper  TAG:c:plates/gold
TAG:create:sleepers
```

> 注意最后几个是**标签**。如果你的输入物所属的标签命中了它们（例如你用一个 `c:plates/*` 里的板），也会撞车。

**推荐做法**：输入物选一个冷门的、确定没被占用的物品（例如 `minecraft:redstone`、`minecraft:amethyst_shard`、`create:zinc_ingot` 目前都是干净的）。

### 7.4 查询某条装配线的当前门槛

```js
console.info('门槛 = ' + MoreFormula.getTier('mypack:a_beyond'))
```

---

## 8. 创作级门槛

`Tier.CREATIVE`（`-1`）是特殊规则：**只有创造级机器能做，其他所有等级（包括超越级）一律拒绝**。

```js
event.recipes.create.pressing('minecraft:nether_star', 'minecraft:diamond')
    .id('mypack:creative_only')
    .tier(Tier.CREATIVE)
```

反过来，**创造级机器可以做任何门槛的配方**（它是「万能」的）。

---

## 9. 脚本放哪里 / `/reload` 行为

| 你用的 API | 脚本放哪 | `/reload` 后 |
|---|---|---|
| `.tier()` | `server_scripts/` | 清空旧值 → 脚本重跑重新写入 ✅ 改脚本立即生效 |
| `MoreFormula.setTier` / `setPrefixTier` | `server_scripts/` | 同上 ✅ |
| `MoreFormulaEvents.registerTier` | `startup_scripts/` | **保留**，不重跑 ⚠️ 改脚本需重启游戏 |

**原理**（读源码确认）：门槛表按来源分两个桶 —— 「服务器脚本桶」和「启动脚本桶」。KubeJS 每次装载**服务器脚本**前（包括 `/reload`）会清空服务器脚本桶，但**不会**清空启动脚本桶（因为启动脚本不会随 `/reload` 重跑）。

> ⚠️ **一个容易踩的坑**：不要从 `startup_scripts` 里调用 `MoreFormula.setTier()`。它写的是**服务器脚本桶**，而服务器脚本装载时会清空这个桶 —— 你在启动脚本里设的门槛会被抹掉。`registerTier` 事件才是启动脚本该用的方式（它写启动脚本桶）。

0.0.1 曾因为完全没有清理逻辑，导致 `/reload` 后门槛无限累积（必须大退游戏）；**0.0.2 已修**。

---

## 10. 排错与自检

### 先看日志

KubeJS 的控制台输出在 `logs/kubejs/server.log`（启动脚本的在 `startup.log`）。用 `console.info(...)` 打点。

### 自检模板

在设置门槛的同一段脚本末尾加：

```js
;[
    'mypack:a_brass',
    'mypack:a_netherite'
].forEach(id => {
    console.info('[门槛自检] ' + id + ' → ' + MoreFormula.getTier(id))
})
console.info('[门槛自检] 当前用到过的全部等级 = ' + MoreFormula.getKnownTiers())
```

`/reload` 后看日志，四种情况：

| 日志表现 | 含义 |
|---|---|
| 等级都对 | ✅ 门槛已生效，问题在机器/摆法 |
| 全是 `0` | 门槛没写进去 —— 检查 `.id()` 是否写错、`.tier()` 是否被吞 |
| 报了 JS 错误 | 脚本本身有问题，往上翻报错栈 |
| 没有输出 | 脚本没被加载 —— 检查文件是否在 `server_scripts/`、扩展名是否 `.js` |

### 判断「机器不给做」是门槛导致的

- 把该配方门槛用 `MoreFormula.removeTier(...)` 删掉（或改成 `0`），`/reload`。
- 若机器立刻能做 → 就是门槛挡的，符合预期。
- 若还是不做 → 不是门槛问题，去查机器有没有动力/转速、流体够不够等。

---

## 11. 常见错误清单

| 症状 | 原因 | 处理 |
|---|---|---|
| 配方永远做不出来 | 未装 CMMM 时给 crushing/milling/cutting 设了门槛，或给 splashing/emptying 设了门槛 | 删掉该门槛，或安装 CMMM |
| 配方永远做不出来 | 门槛设太高，你手上没有对应等级的机器 | 降门槛，或造高级机器 |
| JEI 里找不到这条配方 | 门槛命中的配方会从原版分类移出、进分级分类 | 在 JEI 里找「黄铜级xx」这类**分级分类**标签 |
| 装配线跑到别的线去了 | 起始输入物和已有装配线撞车 | 换一个冷门输入物（见 7.3） |
| 给装配线某一道工序设的等级没生效 | 整条线共用一个门槛，工序级设置无意义 | 拆成多条线（见 7.1） |
| 启动脚本里 `setTier` 设了没效果 | 启动脚本桶被服务器脚本装载清空 | 改用 `registerTier`，或挪到 `server_scripts` |
| 改了脚本 `/reload` 不生效 | 用的是 `registerTier`（启动事件） | 重启游戏，或改用 `setTier` |
| 前缀门槛影响了不该影响的配方 | 前缀命中范围过大 | 改用精确 ID（见方式 C 的警告） |

---

## 12. 完整可跑示例

**一条完整产线**，改一个常量就能切换整条线的等级门槛，
**已核对过本整合包内无输入物/产物冲突**，可直接用。

见游戏内文件：

```
D:\.minecraft\versions\new project\kubejs\server_scripts\more_formula_assembly_test.js
```

它演示了：

- 一条含三种工序的产线（辊压 → 注液 → 机械手），用三种不同机器
- 整条线共用一个 `TIER` 常量，改一行即可在 黄铜 / 下界合金 / 末影 / 超越 之间切换
- `.id()` + `.tier()` 的标准写法
- 脚本内自检日志（`/reload` 后在 `logs/kubejs/server.log` 看门槛是否写对）
- `.tier()` 失效时的备用写法（`MoreFormula.setTier`）

**怎么用它验证闸门真的在拦**：把 `TIER` 设为 `Tier.END`，但故意只放**黄铜**喷口 ——
第 2 道工序应该毫无反应；换成末影级喷口立刻恢复推进。

> 需要多条不同等级的产线时，照抄这份文件、改输入物和 `TIER` 即可 ——
> 但**每条线的起始输入物必须互不相同**（原因见 7.3）。

---

## 附录：API 速查

```js
// ── 链式（server_scripts）
.tier(Tier.END)                          // 给这条配方加门槛

// ── 全局绑定 MoreFormula（server_scripts）
MoreFormula.setTier('create:mixing/xx', Tier.BRASS)     // 按精确 ID
MoreFormula.setPrefixTier('create:mixing/', Tier.BRASS) // 按前缀
MoreFormula.getTier('create:mixing/xx')                 // 查询 → 整数
MoreFormula.removeTier('create:mixing/xx')              // 删除
MoreFormula.getAllTiers()                               // 全部精确条目 Map
MoreFormula.getKnownTiers()                             // 用到过的等级列表（升序）

// ── 启动事件（startup_scripts）
MoreFormulaEvents.registerTier(event => {
    event.setTier(idOrWildcard, tier)
    event.getTier(id)
    event.removeTier(id)
    event.getAllTiers()
})

// ── 等级常量 Tier
Tier.ZERO / BRASS / NETHERITE / END / BEYOND / CREATIVE
```
