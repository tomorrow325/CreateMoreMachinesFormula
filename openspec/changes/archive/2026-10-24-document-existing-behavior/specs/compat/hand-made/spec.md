# Spec Delta

## Purpose

定义可选依赖 Create: Hand Made（0.2.0-beta+）的兼容行为：手工工具不得绕过机器等级门槛，只能处理无门槛配方；以及模组缺席时的隔离保证。

## ADDED Requirements

### Requirement: 手工工具只处理无门槛配方

Create: Hand Made 的手工工具（手锯、研钵、压锤、灌注枪、指杆、搅拌棒、风箱）SHALL 只能处理门槛为 `Tier.ZERO`（未设门槛）的配方；设了任何门槛（含普通四级与创造级）的配方 MUST NOT 被手工工具执行，防止以手工方式绕过机器等级门槛。

#### Scenario: 手工工具被门槛拦截

- **WHEN** 某配方被设 `Tier.BRASS` 门槛，玩家用手锯或研钵尝试执行
- **THEN** 手工工具不执行该配方

#### Scenario: 无门槛配方可手工执行

- **WHEN** 配方未设门槛，玩家使用手工工具
- **THEN** 手工工具正常执行，行为与未安装本模组时一致

### Requirement: 空值输入按拒绝处理

手工工具查询配方得到空/缺失结果时，兼容层 SHALL 按「拒绝」语义处理（无可放行配方），不得因空值放行任何配方。

#### Scenario: 空查询无放行

- **WHEN** 手工工具的候选配方列表为空或缺失
- **THEN** 兼容层返回空的可执行配方集合

### Requirement: 模组缺席不生效

未安装 Create: Hand Made 时，本兼容 SHALL 完全不生效：游戏正常启动、无报错，相关闸门逻辑不被加载，其余功能不受影响。

#### Scenario: 缺席 Hand Made 正常启动

- **WHEN** 游戏实例未安装 Create: Hand Made
- **THEN** 游戏正常启动，本模组其余功能不变，日志无兼容层错误
