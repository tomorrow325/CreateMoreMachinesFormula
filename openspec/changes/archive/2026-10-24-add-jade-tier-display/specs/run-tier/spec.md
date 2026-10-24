# Spec Delta

## ADDED Requirements

### Requirement: Jade HUD 显示

安装 Jade（可选依赖）时，系统 SHALL 在其方块信息 HUD 中为「可调机器名单」内的机器追加与工程师护目镜完全同源的两行：「机械等级」（出厂等级）与「运行等级」（当前生效值，未设置时等于出厂等级）。显示 MUST NOT 要求玩家佩戴工程师护目镜。判定规则 MUST 与护目镜显示共用同一份名单与同一套解析（含破碎轮「轮子 → 控制器」反查：反查不到控制器的半结构不显示）；名单外机器（含分级鼓风机）MUST NOT 显示。未安装 Jade 时本模组行为 MUST NOT 受任何影响（不加载任何 Jade 相关类、无报错）。运行等级数据 SHALL 经由既有的区块同步到达客户端，MUST NOT 为 Jade 单独新增服务端数据通道。

#### Scenario: 免护目镜查看运行等级

- **WHEN** 玩家未戴护目镜、准星指向已用扳手调低运行等级的分级锯，且安装了 Jade
- **THEN** Jade 顶部 HUD 显示「机械等级」与「运行等级」两行，运行等级为调低后的档位

#### Scenario: 名单外机器不显示

- **WHEN** 准星指向分级鼓风机或原版 Create 机器
- **THEN** Jade HUD 不出现这两行

#### Scenario: Jade 缺席零影响

- **WHEN** 未安装 Jade 启动游戏（`runClientNoOpt` 场景）
- **THEN** 加载与游戏过程无任何 Jade 相关报错，护目镜显示与既有行为一致
