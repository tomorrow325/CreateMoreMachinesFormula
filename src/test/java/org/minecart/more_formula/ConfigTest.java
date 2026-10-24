package org.minecart.more_formula;

// Config 逻辑的离线回归测试。
//
// 为什么需要它：Config 是纯静态、不依赖 Minecraft 运行时的类（只用到 ResourceLocation），
// 因此可以直接在 JVM 里跑，把「修没修好」变成可执行的断言，而不是靠肉眼看代码。
// 覆盖本次修复中的三条：热重载不累积（A）、前缀门槛进 knownTiers（C）、
// 双 map 合并后的优先级（D）。

import net.minecraft.resources.ResourceLocation;
import org.minecart.more_formula.compat.handmade.HandMadeRecipeGate;
import org.minecart.more_formula.compat.handmade.MoreFormulaHandMadeMixinConfigPlugin;
import org.minecart.more_formula.compat.mekanicalcreate.MekanicalCreateRecipeGate;
import org.minecart.more_formula.compat.mekanicalcreate.MekanicalCreateSpeedConfig;
import org.minecart.more_formula.compat.mekanicalcreate.MoreFormulaMekanicalCreateMixinConfigPlugin;
import org.minecart.more_formula.util.CrushingWheelPair;
import org.minecart.more_formula.util.MachineRoster;
import org.minecart.more_formula.util.TierHelper;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class ConfigTest {

    private static int passed;
    private static int failed;

    private static final Config.Source SERVER = Config.Source.SERVER;
    private static final Config.Source STARTUP = Config.Source.STARTUP;

    public static void main(String[] args) {
        // ---- D：精确命中最优先，且 SERVER 覆盖 STARTUP ----
        Config.clearAll();
        Config.addTier(rl("create:mixing/a"), 1, STARTUP);
        Config.addTier(rl("create:mixing/a"), 3, SERVER);
        check("精确命中 SERVER 优先", () -> Config.getRequiredTier(rl("create:mixing/a")) == 3);

        // ---- D：前缀最长匹配优先 ----
        Config.clearAll();
        Config.addPrefixTier("create:mixing/", 1, SERVER);
        Config.addPrefixTier("create:mixing/brass", 4, SERVER);
        check("前缀最长匹配优先", () -> Config.getRequiredTier(rl("create:mixing/brass_ingot")) == 4);
        check("短前缀仍然生效", () -> Config.getRequiredTier(rl("create:mixing/copper_ingot")) == 1);

        // ---- D：精确命中压过前缀 ----
        Config.clearAll();
        Config.addPrefixTier("create:mixing/", 1, SERVER);
        Config.addTier(rl("create:mixing/brass_ingot"), 4, SERVER);
        check("精确命中压过前缀", () -> Config.getRequiredTier(rl("create:mixing/brass_ingot")) == 4);

        // ---- D：未命中的配方不受门槛影响 ----
        check("未命中返回 0", () -> Config.getRequiredTier(rl("minecraft:bread")) == 0);

        // ---- C：只配前缀门槛时，等级必须出现在 knownTiers ----
        Config.clearAll();
        Config.addPrefixTier("create:mixing/", 2, SERVER);
        check("仅有前缀门槛时 knownTiers 含该等级", () -> Config.getKnownTiers().equals(List.of(2)));

        // ---- C：精确 + 前缀混合，去重且升序 ----
        Config.clearAll();
        Config.addTier(rl("create:pressing/x"), 3, SERVER);
        Config.addPrefixTier("create:mixing/", 2, SERVER);
        Config.addPrefixTier("create:filling/", 2, SERVER);
        Config.addTier(rl("create:pressing/y"), 3, SERVER);
        check("knownTiers 去重升序", () -> Config.getKnownTiers().equals(List.of(2, 3)));

        // ---- A：模拟 /reload —— 清 SERVER 桶后重新写入，条目数不增长 ----
        Config.clearAll();
        Config.addTier(rl("create:pressing/x"), 3, SERVER);
        Config.addPrefixTier("create:mixing/", 2, SERVER);
        int exactAfterFirst = Config.getAllTiers().size();
        for (int i = 0; i < 20; i++) {
            Config.clear(Config.Source.SERVER);          // = beforeScriptsLoaded 做的事
            Config.addTier(rl("create:pressing/x"), 3, SERVER);
            Config.addPrefixTier("create:mixing/", 2, SERVER);
        }
        check("重载 20 次后精确条目不增长", () -> Config.getAllTiers().size() == exactAfterFirst);
        check("重载后门槛值仍然正确", () -> Config.getRequiredTier(rl("create:pressing/x")) == 3
                && Config.getRequiredTier(rl("create:mixing/z")) == 2);

        // ---- A：STARTUP 桶不被 /reload 清掉 ----
        Config.clearAll();
        Config.addTier(rl("create:pressing/startup"), 4, STARTUP);
        Config.addTier(rl("create:pressing/server"), 1, SERVER);
        Config.clear(Config.Source.SERVER);
        check("reload 保留 STARTUP 条目", () -> Config.getRequiredTier(rl("create:pressing/startup")) == 4);
        check("reload 清掉 SERVER 条目", () -> Config.getRequiredTier(rl("create:pressing/server")) == 0);

        // ---- A：同一个前缀重复注册保持幂等（不因重复 append 膨胀）----
        Config.clearAll();
        for (int i = 0; i < 50; i++) {
            Config.addPrefixTier("create:mixing/", 1, SERVER);
        }
        check("重复注册同一前缀保持幂等", () -> Config.getRequiredTier(rl("create:mixing/anything")) == 1
                && Config.getKnownTiers().equals(List.of(1)));

        // ---- creative 的 -1 语义 ----
        Config.clearAll();
        Config.addTier(rl("create:pressing/creative"), Config.CREATIVE_TIER, SERVER);
        check("creative 门槛生效", () -> Config.getRequiredTier(rl("create:pressing/creative")) == -1);
        check("creative 出现在 knownTiers", () -> Config.getKnownTiers().equals(List.of(-1)));

        // ---- 非法值被忽略（0 表示无门槛，不建条目）----
        Config.clearAll();
        Config.addTier(rl("create:pressing/zero"), 0, SERVER);
        Config.addTier(rl("create:pressing/big"), 9, SERVER);
        check("0 与越界值不入表", () -> Config.getKnownTiers().isEmpty()
                && Config.getRequiredTier(rl("create:pressing/zero")) == 0);

        // ---- removeTier 跨来源生效 ----
        Config.clearAll();
        Config.addTier(rl("create:pressing/x"), 2, STARTUP);
        Config.addTier(rl("create:pressing/x"), 3, SERVER);
        Config.removeTier(rl("create:pressing/x"));
        check("removeTier 清掉两个来源", () -> Config.getRequiredTier(rl("create:pressing/x")) == 0);

        // ---- 通配符写法（带 *）与前缀等价 ----
        Config.clearAll();
        Config.addPrefixTier("create:mixing/*", 2, SERVER);
        check("带 * 的前缀当成通配", () -> Config.getRequiredTier(rl("create:mixing/abc")) == 2);

        // ---- 空/异常输入不炸 ----
        Config.clearAll();
        Config.addPrefixTier("", 2, SERVER);
        Config.addPrefixTier(null, 2, SERVER);
        check("空前缀被忽略", () -> Config.getKnownTiers().isEmpty());
        check("null 配方 id 安全", () -> Config.getRequiredTier(null) == 0);

        // ---- Mekanical-Create：CMM 模块等级与派生配方 ID ----
        check("普通 Create 模块为 0 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("create:mechanical_press")) == 0);
        check("CMM brass 模块为 1 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:brass_mechanical_press")) == 1);
        check("CMM netherite 模块为 2 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:netherite_mechanical_press")) == 2);
        check("CMM end 模块为 3 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:end_mechanical_press")) == 3);
        check("CMM beyond 模块为 4 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:beyond_mechanical_press")) == 4);
        check("CMM creative 模块为创造级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:creative_mechanical_press")) == Config.CREATIVE_TIER);
        check("识别 CMM 压机类别", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremachines:netherite_mechanical_press"))
                == MekanicalCreateRecipeGate.ModuleKind.PRESS);
        check("识别 CMM 部署器类别", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremachines:beyond_deployer"))
                == MekanicalCreateRecipeGate.ModuleKind.DEPLOYER);
        check("识别 CMM 搅拌器类别", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremachines:brass_mechanical_mixer"))
                == MekanicalCreateRecipeGate.ModuleKind.MIXER);
        check("识别 CMM 喷口类别", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremachines:end_spout"))
                == MekanicalCreateRecipeGate.ModuleKind.SPOUT);
        check("非机器 CMM 物品不被接受", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremachines:netherite_casing"))
                == MekanicalCreateRecipeGate.ModuleKind.NONE);
        check("普通槽接受 CMM 压机", () -> MekanicalCreateRecipeGate.isSupportedModule(
                rl("createmoremachines:netherite_mechanical_press"), false));
        check("普通槽接受 CMM 部署器", () -> MekanicalCreateRecipeGate.isSupportedModule(
                rl("createmoremachines:brass_deployer"), false));
        check("非流体槽拒绝 CMM 搅拌器", () -> !MekanicalCreateRecipeGate.isSupportedModule(
                rl("createmoremachines:brass_mechanical_mixer"), false));
        check("流体槽接受 CMM 搅拌器", () -> MekanicalCreateRecipeGate.isSupportedModule(
                rl("createmoremachines:brass_mechanical_mixer"), true));
        check("非流体槽拒绝 CMM 喷口", () -> !MekanicalCreateRecipeGate.isSupportedModule(
                rl("createmoremachines:netherite_spout"), false));
        check("CMM 压机匹配 Create 压机配方分支", () -> MekanicalCreateRecipeGate.matchesCreateModule(
                rl("createmoremachines:netherite_mechanical_press"), rl("create:mechanical_press"), true));
        check("CMM 搅拌器在流体开关关闭时不映射", () -> !MekanicalCreateRecipeGate.matchesCreateModule(
                rl("createmoremachines:brass_mechanical_mixer"), rl("create:mechanical_mixer"), false));
        check("CMM 模块不冒充其他 Create 机器", () -> !MekanicalCreateRecipeGate.matchesCreateModule(
                rl("createmoremachines:netherite_mechanical_press"), rl("create:deployer"), true));
        check("CMM 压机匹配 pressing 序列步骤", () -> MekanicalCreateRecipeGate.matchesSequenceModule(
                rl("createmoremachines:netherite_mechanical_press"), rl("create:mechanical_press")));
        check("CMM 喷口匹配 filling 序列步骤", () -> MekanicalCreateRecipeGate.matchesSequenceModule(
                rl("createmoremachines:end_spout"), rl("create:spout")));
        check("Mekanical-Create 派生 ID还原原配方", () -> MekanicalCreateRecipeGate.sourceRecipeId(
                rl("create:pressing/iron/mekanicalcreate_pressing")).equals(
                rl("create:pressing/iron")));
        check("Mekanical-Create JEI 双派生 ID还原原配方", () -> MekanicalCreateRecipeGate.sourceRecipeId(
                rl("create:pressing/iron/mekanicalcreate_pressing/mekanicalcreate_createmoremachines_brass_mechanical_press"))
                .equals(rl("create:pressing/iron")));
        check("普通模块不能执行 NETHERITE 配方", () -> !MekanicalCreateRecipeGate.isAllowed(
                0, 2));
        check("NETHERITE 模块可以执行 NETHERITE 配方", () -> MekanicalCreateRecipeGate.isAllowed(
                2, 2));
        check("JEI 最低机器与黄铜门槛匹配", () -> MekanicalCreateRecipeGate.isMinimumTier(1, 1));
        check("JEI 不把低级机器显示为最低机器", () -> !MekanicalCreateRecipeGate.isMinimumTier(1, 2));
        check("JEI 不为高级机器重复显示低门槛配方", () -> !MekanicalCreateRecipeGate.isMinimumTier(3, 2));
        check("JEI 创造门槛只匹配创造模块", () -> MekanicalCreateRecipeGate.isMinimumTier(
                Config.CREATIVE_TIER, Config.CREATIVE_TIER)
                && !MekanicalCreateRecipeGate.isMinimumTier(4, Config.CREATIVE_TIER));
        check("JEI 无门槛配方不生成分级机器行", () -> !MekanicalCreateRecipeGate.isMinimumTier(1, 0));

        // ---- Mekanical-Create：按机器类别和 CMM 等级读取倍率 ----
        check("Brass 默认 1 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(1) == 1);
        check("Netherite 默认 2 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(2) == 2);
        check("End 默认 3 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(3) == 3);
        check("Beyond 默认 4 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(4) == 4);
        check("Creative 默认最高 4 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(-1) == 4);
        check("未知 tier 默认 1 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(0) == 1);
        check("按机器类别隔离配置项", () -> MekanicalCreateSpeedConfig.configuredTierCount(
                MekanicalCreateRecipeGate.ModuleKind.PRESS) == 5
                && MekanicalCreateSpeedConfig.configuredTierCount(
                MekanicalCreateRecipeGate.ModuleKind.DEPLOYER) == 5
                && MekanicalCreateSpeedConfig.configuredTierCount(
                MekanicalCreateRecipeGate.ModuleKind.MIXER) == 5
                && MekanicalCreateSpeedConfig.configuredTierCount(
                MekanicalCreateRecipeGate.ModuleKind.SPOUT) == 5);
        check("普通 Create 模块无加成", () -> MekanicalCreateSpeedConfig.getMultiplier(
                rl("create:mechanical_press")) == 1);
        check("并行数按倍率放大", () -> MekanicalCreateSpeedConfig.scaleParallelCount(9, 4) == 36);
        check("work budget 按倍率放大", () -> MekanicalCreateSpeedConfig.scaleWorkBudget(40, 3) == 120);
        check("倍率不低于 1", () -> MekanicalCreateSpeedConfig.clampMultiplier(0) == 1);
        check("倍率上限为 64", () -> MekanicalCreateSpeedConfig.clampMultiplier(100) == 64);
        check("work budget 溢出饱和", () -> MekanicalCreateSpeedConfig.scaleWorkBudget(
                Long.MAX_VALUE, 2) == Long.MAX_VALUE);
        check("press Netherite 默认 2 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(2) == 2);
        check("deployer End 默认 3 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(3) == 3);
        check("mixer Beyond 默认 4 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(4) == 4);
        check("spout Creative 默认 4 倍", () -> MekanicalCreateSpeedConfig.defaultMultiplier(-1) == 4);
        check("多个催化剂使用最高倍率而非相乘", () -> MekanicalCreateSpeedConfig.getHighestMultiplier(
                List.of(2, 3, 1)) == 3);

        // ---- CMM 3.0（CMMM 已并入）：CMM 等级值 → 本模组等级值的平移 ----
        // 分级破碎轮/锯复用 CMM 的 CMMTier（brass=2/netherite=3/end=4/beyond=5/creative=-1），
        // 与既有 CMM 机器共用同一映射，不能因接入新机器而漂移。
        check("CMM brass 等级平移为 1 级", () -> TierHelper.toFormulaTier(2) == 1);
        check("CMM netherite 等级平移为 2 级", () -> TierHelper.toFormulaTier(3) == 2);
        check("CMM end 等级平移为 3 级", () -> TierHelper.toFormulaTier(4) == 3);
        check("CMM beyond 等级平移为 4 级", () -> TierHelper.toFormulaTier(5) == 4);
        check("CMM creative 等级保持创造级", () -> TierHelper.toFormulaTier(-1) == Config.CREATIVE_TIER);
        check("CMM 等级 1 原样保留", () -> TierHelper.toFormulaTier(1) == 1);
        check("CMM 等级 0 原样保留", () -> TierHelper.toFormulaTier(0) == 0);

        // ---- 门槛判定的纯函数语义（0 级机器拒绝一切门槛配方） ----
        check("0 级机器拒绝普通门槛", () -> !TierHelper.isTierAllowed(0, 2));
        check("同级机器放行", () -> TierHelper.isTierAllowed(2, 2));
        check("高级机器放行低门槛", () -> TierHelper.isTierAllowed(3, 2));
        check("创造级机器放行普通门槛", () -> TierHelper.isTierAllowed(Config.CREATIVE_TIER, 2));
        check("0 级机器拒绝创造级门槛", () -> !TierHelper.isTierAllowed(0, Config.CREATIVE_TIER));
        check("创造级门槛只放行创造级机器", () -> TierHelper.isTierAllowed(Config.CREATIVE_TIER, Config.CREATIVE_TIER)
                && !TierHelper.isTierAllowed(4, Config.CREATIVE_TIER));
        check("无门槛配方对所有机器放行", () -> TierHelper.isTierAllowed(0, 0));

        // ---- 破碎轮「轮子 ↔ 控制器」配对：CMM 3.0 类名判定（纯字符串，可离线断言）----
        check("CMM 3.0 分级控制器是控制器", () -> CrushingWheelPair.isControllerByName(
                "net.yxiao233.createmoremachines.api.content.crushing_wheel.CMMCrushingWheelControllerBlockEntity"));
        check("原版控制器也是控制器（护目镜反查需要）", () -> CrushingWheelPair.isControllerByName(
                "com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity"));
        check("压机不是控制器", () -> !CrushingWheelPair.isControllerByName(
                "net.yxiao233.createmoremachines.api.content.mechanical.press.CMMMechanicalPressBlockEntity"));
        check("CMM 3.0 分级轮子是轮子且可调", () -> CrushingWheelPair.isTieredWheelByName(
                "net.yxiao233.createmoremachines.api.content.crushing_wheel.CMMCrushingWheelBlockEntity"));
        check("原版轮子是轮子但不可调", () -> !CrushingWheelPair.isTieredWheelByName(
                "com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity")
                && CrushingWheelPair.isWheelByName(
                        "com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity"));
        check("旧 CMMM 包名不再命中任何判定", () ->
                !CrushingWheelPair.isControllerByName(
                        "net.tomorrow325.createmoremoremachines.api.content.crushing_wheel.CMMCrushingWheelControllerBlockEntity")
                        && !CrushingWheelPair.isWheelByName(
                        "net.tomorrow325.createmoremoremachines.api.content.crushing_wheel.CMMCrushingWheelBlockEntity"));

        // ---- Create: Hand Made：共享闸门的空值面（加固前遍历 null 会 NPE）----
        check("onlyTierZero 对 null 输入返回空", () -> HandMadeRecipeGate.onlyTierZero(null).isEmpty());
        check("onlyTierZero 对空列表返回空", () -> HandMadeRecipeGate.onlyTierZero(List.of()).isEmpty());
        check("onlyTierZero 跳过非 holder 元素", () -> HandMadeRecipeGate.onlyTierZero(List.of("不是 holder")).isEmpty());
        check("isTierZero 对 null holder 为 false", () -> !HandMadeRecipeGate.isTierZero(null));
        check("isTierZeroResult 对 null 为 false", () -> !HandMadeRecipeGate.isTierZeroResult(null));
        check("isTierZeroResult 对非 holder 为 false", () -> !HandMadeRecipeGate.isTierZeroResult("x"));

        // ---- Mekanical-Create：CMM 3.0 分级破碎轮/锯的单命名空间映射 ----
        check("识别 CMM 3.0 分级锯类别", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremachines:brass_mechanical_saw"))
                == MekanicalCreateRecipeGate.ModuleKind.SAW);
        check("识别 CMM 3.0 分级破碎轮类别", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremachines:netherite_crushing_wheel"))
                == MekanicalCreateRecipeGate.ModuleKind.CRUSHING_WHEEL);
        check("非机器 CMM 物品不被接受", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremachines:netherite_casing"))
                == MekanicalCreateRecipeGate.ModuleKind.NONE);
        check("CMM brass 分级锯为 1 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:brass_mechanical_saw")) == 1);
        check("CMM netherite 分级锯为 2 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:netherite_mechanical_saw")) == 2);
        check("CMM end 分级破碎轮为 3 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:end_crushing_wheel")) == 3);
        check("CMM beyond 分级破碎轮为 4 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:beyond_crushing_wheel")) == 4);
        check("CMM creative 分级锯为创造级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:creative_mechanical_saw")) == Config.CREATIVE_TIER);
        check("CMM 无前缀物品为 0 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremachines:casing")) == 0);
        check("create 命名空间档位前缀仍为 0 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("create:beyond_mechanical_saw")) == 0);
        check("普通槽接受 CMM 分级锯", () -> MekanicalCreateRecipeGate.isSupportedModule(
                rl("createmoremachines:brass_mechanical_saw"), false));
        check("普通槽接受 CMM 分级破碎轮", () -> MekanicalCreateRecipeGate.isSupportedModule(
                rl("createmoremachines:end_crushing_wheel"), false));
        check("CMM 分级锯匹配 Create 锯配方分支", () -> MekanicalCreateRecipeGate.matchesCreateModule(
                rl("createmoremachines:brass_mechanical_saw"), rl("create:mechanical_saw"), true));
        check("CMM 分级破碎轮匹配 Create 破碎轮分支", () -> MekanicalCreateRecipeGate.matchesCreateModule(
                rl("createmoremachines:netherite_crushing_wheel"), rl("create:crushing_wheel"), true));
        check("CMM 分级锯不冒充其他 Create 机器", () -> !MekanicalCreateRecipeGate.matchesCreateModule(
                rl("createmoremachines:brass_mechanical_saw"), rl("create:deployer"), true));
        check("CMM 分级锯匹配 sawing 序列步骤", () -> MekanicalCreateRecipeGate.matchesSequenceModule(
                rl("createmoremachines:brass_mechanical_saw"), rl("create:mechanical_saw")));
        check("CMM 分级破碎轮不映射序列步骤", () -> !MekanicalCreateRecipeGate.matchesSequenceModule(
                rl("createmoremachines:netherite_crushing_wheel"), rl("create:crushing_wheel")));

        // ---- 退役的 createmoremoremachines 命名空间不再识别（独立 CMMM 已并入 CMM 3.0）----
        check("旧 CMMM 命名空间分级锯不再识别", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremoremachines:brass_mechanical_saw"))
                == MekanicalCreateRecipeGate.ModuleKind.NONE);
        check("旧 CMMM 命名空间分级破碎轮不再识别", () -> MekanicalCreateRecipeGate.getModuleKind(
                rl("createmoremoremachines:netherite_crushing_wheel"))
                == MekanicalCreateRecipeGate.ModuleKind.NONE);
        check("旧 CMMM 命名空间物品一律 0 级", () -> MekanicalCreateRecipeGate.getModuleTier(
                rl("createmoremoremachines:creative_mechanical_saw")) == 0);

        // ---- Mekanical-Create：CMMM 新模块的倍率配置段（不触发 IntValue.get，仅看段注册）----
        check("分级破碎轮/锯各有独立 5 档配置段", () ->
                MekanicalCreateSpeedConfig.configuredTierCount(
                        MekanicalCreateRecipeGate.ModuleKind.CRUSHING_WHEEL) == 5
                        && MekanicalCreateSpeedConfig.configuredTierCount(
                        MekanicalCreateRecipeGate.ModuleKind.SAW) == 5);

        // ---- mixin 配置插件门控：模组不在场时门必须关死 ----
        // 离线 JVM 未引导 FML，FMLLoader.getLoadingModList() 返回 null，插件判空后按
        // 「可选依赖不在场」处理 —— 这是「未装手搓学/Mekanical-Create 时零回归」的
        // 机制半边：mixin 不注入 → 世界内不执行。
        // 「模组在场 → 门打开」的另一半需要真实 FML 运行时，只能进游戏验证。
        MoreFormulaHandMadeMixinConfigPlugin handMadePlugin = new MoreFormulaHandMadeMixinConfigPlugin();
        handMadePlugin.onLoad("mixin.more_formula.handmade");
        check("手搓学不在场时其 mixin 不应用", () -> !handMadePlugin.shouldApplyMixin(
                "com.simibubi.create.AllItems",
                "org.minecart.more_formula.compat.handmade.mixin.HandSawItemMixin"));

        MoreFormulaMekanicalCreateMixinConfigPlugin mekanicalPlugin = new MoreFormulaMekanicalCreateMixinConfigPlugin();
        mekanicalPlugin.onLoad("mixin.more_formula.mekanicalcreate");
        check("Mekanical-Create 不在场时其 mixin 不应用", () -> !mekanicalPlugin.shouldApplyMixin(
                "com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity",
                "org.minecart.more_formula.compat.mekanicalcreate.mixin.SimulationRecipeResolverMixin"));

        // ---- 新增：同输入多配方择优（复现 yunkuang.js 的 t1/t2 场景）----
        selectionTests();

        System.out.println();
        System.out.println("PASSED=" + passed + " FAILED=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // =========================================================================
    //  「同输入多配方择优」回归
    // =========================================================================
    //
    //  用户报告的原 bug：yunkuang.js 里对同一输入 maixr:yunkuang_zazhi 写了
    //  两条 create:crushing（t1 / t2），给下界粉碎轮喂料时出来的却是黄铜粉碎轮的
    //  产物 —— 因为 Create 的 AllRecipeTypes.find 只回「注册顺序第一条」，
    //  而旧实现只在这条上问「允许吗」，命中 t1 就放行 t1。
    //
    //  断言打在最底层的 pickBestIndexForTier(机器等级, id 列表) 上：它不碰任何
    //  Minecraft 类型（Recipe 的静态初始化要求游戏注册表已 bootstrap，离线构造
    //  RecipeHolder 会抛 "Not bootstrapped"），但选择的全部规则都在这一层。
    // =========================================================================

    private static List<ResourceLocation> ids(String... list) {
        List<ResourceLocation> out = new java.util.ArrayList<>();
        for (String s : list) {
            out.add(rl(s));
        }
        return out;
    }

    /** 返回被选中的 id，未选中任何一条时返回 "<none>"。 */
    private static String picked(int machineTier, List<ResourceLocation> list) {
        int i = TierHelper.pickBestIndexForTier(machineTier, list);
        return i < 0 ? "<none>" : list.get(i).toString();
    }

    /** 复现用户场景：同输入 t1(黄铜=1) 与 t2(下界=2)，t1 先注册。 */
    private static void selectionTests() {
        Config.clearAll();
        Config.addTier(rl("maixr:yunkuang_zazhi_crushing_t1"), 1, SERVER);
        Config.addTier(rl("maixr:yunkuang_zazhi_crushing_t2"), 2, SERVER);
        List<ResourceLocation> both = ids(
                "maixr:yunkuang_zazhi_crushing_t1",
                "maixr:yunkuang_zazhi_crushing_t2");

        // 核心断言：下界粉碎轮（2 级）必须执行 t2，而不是先注册的 t1。
        check("同输入 t1/t2：下界级(2) 选 t2",
                () -> picked(2, both).equals("maixr:yunkuang_zazhi_crushing_t2"));
        check("同输入 t1/t2：末影级(3) 选 t2",
                () -> picked(3, both).equals("maixr:yunkuang_zazhi_crushing_t2"));
        check("同输入 t1/t2：超越级(4) 选 t2",
                () -> picked(4, both).equals("maixr:yunkuang_zazhi_crushing_t2"));
        // 黄铜级(1) 只够 t1，必须退到 t1（不能因为 t2 存在就整个拒绝）。
        check("同输入 t1/t2：黄铜级(1) 退到 t1",
                () -> picked(1, both).equals("maixr:yunkuang_zazhi_crushing_t1"));
        // 原版机器(0) 两条都不够 → 空（闸门拦截）。
        check("同输入 t1/t2：原版级(0) 两条都不够 → 空",
                () -> picked(0, both).equals("<none>"));
        // 创造级(-1) 通行一切，取最高的 t2。
        check("同输入 t1/t2：创造级(-1) 选 t2",
                () -> picked(-1, both).equals("maixr:yunkuang_zazhi_crushing_t2"));

        // 三条同输入配方：3 级机器取 3 级那条（够得着的最高）。
        Config.clearAll();
        Config.addTier(rl("t:a1"), 1, SERVER);
        Config.addTier(rl("t:a2"), 3, SERVER);
        Config.addTier(rl("t:a3"), 4, SERVER);
        List<ResourceLocation> three = ids("t:a1", "t:a2", "t:a3");
        check("三档同输入：3 级机器取 t:a2（够得着的最高）",
                () -> picked(3, three).equals("t:a2"));
        check("三档同输入：4 级机器取 t:a3",
                () -> picked(4, three).equals("t:a3"));
        check("三档同输入：1 级机器取 t:a1",
                () -> picked(1, three).equals("t:a1"));

        // 并列（同门槛）时保留候选顺序里的第一条 —— 保证确定性。
        Config.clearAll();
        Config.addTier(rl("t:first"), 2, SERVER);
        Config.addTier(rl("t:second"), 2, SERVER);
        check("同门槛并列：取候选顺序第一条",
                () -> picked(4, ids("t:first", "t:second")).equals("t:first"));

        // 无门槛配方（0 级）不应压过设了门槛的同输入配方。
        Config.clearAll();
        Config.addTier(rl("t:gated"), 2, SERVER);
        check("无门槛配方不压过带门槛配方",
                () -> picked(2, ids("t:plain", "t:gated")).equals("t:gated"));
        check("只有无门槛配方时被选中",
                () -> picked(0, ids("t:plain")).equals("t:plain"));

        // 创造级(-1) 是独占档：普通机器一条都不许选，即使它是最高级机器。
        Config.clearAll();
        Config.addTier(rl("t:creative"), -1, SERVER);
        Config.addTier(rl("t:beyond"), 4, SERVER);
        check("创造专用不落给超越级机器",
                () -> picked(4, ids("t:creative", "t:beyond")).equals("t:beyond"));
        check("创造级机器选创造专用那条（rank 高于常规）",
                () -> picked(-1, ids("t:creative", "t:beyond")).equals("t:creative"));

        // 边界：空候选 / 含 null 候选 → 不抛异常。
        check("空候选 → <none>", () -> picked(4, ids()).equals("<none>"));
        check("null 候选列表 → <none>",
                () -> TierHelper.pickBestIndexForTier(4, null) == -1);
        // 归一语义（见 TierHelper#rankIndicesForTier 注释）：null id = 查不到门槛，
        // 按「无门槛」处理并保留 —— 因为列表型机器（锯/工作盆）绝不能丢掉一条
        // 只是反查失败的配方。于是它排在带门槛的候选之后、与原顺序内部稳定。
        check("候选含 null 项时按无门槛保留（不跳过）",
                () -> TierHelper.pickBestIndexForTier(4,
                        java.util.Arrays.asList(null, rl("t:x"))) == 0);
        check("候选含 null 项且另有高门槛时，高门槛优先",
                () -> {
                    Config.clearAll();
                    Config.addTier(rl("t:hi"), 3, SERVER);
                    return TierHelper.pickBestIndexForTier(4,
                            java.util.Arrays.asList(null, rl("t:hi"))) == 1;
                });

        orderingTests();
        runningTierTests();
    }

    // =========================================================================
    //  「运行等级」（扳手手动调节）回归
    // =========================================================================
    //
    //  规则（已与需求逐条确认）：
    //   - 只能调低不能调高：循环范围是 0..真实等级
    //   - 循环到底回到顶（真实等级）
    //   - 0 = 只做无门槛配方
    //   - 创造级机器例外：循环含创造档，顺序 0 → -1 → 4 → 3 → 2 → 1 → 0
    //   - 创造级机器的“运行等级”不影响准入（恒可做任何配方），只影响择优
    //   - 0 级机器（原版机器）没有可调空间
    // =========================================================================

    private static void runningTierTests() {
        final int UNSET = PriorityRules.UNSET;

        // ---- 普通分级机器（下界合金 = 2）：2 → 1 → 0 → 2 ----
        check("循环：未设置(下界2) → 1", () -> PriorityRules.cycle(UNSET, 2) == 1);
        check("循环：1 → 0", () -> PriorityRules.cycle(1, 2) == 0);
        check("循环：0 → 回到真实等级 2", () -> PriorityRules.cycle(0, 2) == 2);
        check("循环：2 → 1（显式传入当前值也一致）", () -> PriorityRules.cycle(2, 2) == 1);

        // ---- 黄铜机器（1）：1 → 0 → 1 ----
        check("循环：未设置(黄铜1) → 0", () -> PriorityRules.cycle(UNSET, 1) == 0);
        check("循环：0 → 回到 1", () -> PriorityRules.cycle(0, 1) == 1);

        // ---- 关键约束：不能调高 ----
        check("不能调高：4 级机器从 0 循环只回到 4，不越过真实等级",
                () -> PriorityRules.cycle(0, 4) == 4);
        // 遍历一整个循环，确认从不出现 > realTier 的值
        check("循环全程不超过真实等级", () -> {
            int t = UNSET;
            for (int i = 0; i < 10; i++) {
                t = PriorityRules.cycle(t, 3);
                if (t > 3) {
                    return false;
                }
            }
            return true;
        });
        // 遍历一整圈应恰好回到起点（下界机器：未设置(≡2) → 1 → 0 → 2，3 步闭环）
        check("循环 3 步闭环回 2（下界 2 级）", () -> {
            int t = UNSET;
            t = PriorityRules.cycle(t, 2); // UNSET(≡2) → 1
            t = PriorityRules.cycle(t, 2); // 1 → 0
            t = PriorityRules.cycle(t, 2); // 0 → 2（回到真实等级）
            return t == 2;
        });

        // ---- 0 级机器无可调空间 ----
        check("0 级机器：cycle 返回 0（不可调）", () -> PriorityRules.cycle(UNSET, 0) == 0);
        check("0 级机器：即便当前值异常也返回 0", () -> PriorityRules.cycle(3, 0) == 0);
        // 「可调空间」必须单独判定，不能靠 cycle 的结果反推：
        // cycle(UNSET, 0) == 0，而 UNSET == Integer.MIN_VALUE，两者不相等 ——
        // 只用 `下一档 == 当前档` 会把 0 级机器误判成可调，于是扳手白写一次 0
        // 并弹出「已调整为 无」这个什么都没改变的提示。
        check("0 级机器：isAdjustable=false（正是上一条陷阱的守卫）",
                () -> !PriorityRules.isAdjustable(0));
        check("0 级机器：cycle 的结果确实不等于 UNSET（所以守卫必须靠 isAdjustable）",
                () -> PriorityRules.cycle(UNSET, 0) != UNSET);
        check("创造级 -1：isAdjustable=true", () -> PriorityRules.isAdjustable(-1));
        check("分级机器 1..4：isAdjustable=true", () -> PriorityRules.isAdjustable(1)
                && PriorityRules.isAdjustable(2)
                && PriorityRules.isAdjustable(3)
                && PriorityRules.isAdjustable(4));
        check("异常等级 5 / -2：isAdjustable=false（按不可调处理，不越界调）",
                () -> !PriorityRules.isAdjustable(5) && !PriorityRules.isAdjustable(-2));

        machineRosterTests();

        // ---- 创造级机器：循环含创造档，顺序 4 → 3 → 2 → 1 → 0 → -1(创造) → 4 ----
        // 注意「未设置」的含义是「≡ 真实等级」，创造机器真实等级就是 -1，
        // 所以第一次循环相当于从 -1 推进 → 回到最高普通档 4。
        check("创造机：未设置(≡创造档) → 4", () -> PriorityRules.cycle(UNSET, -1) == 4);
        check("创造机：4 → 3", () -> PriorityRules.cycle(4, -1) == 3);
        check("创造机：3 → 2", () -> PriorityRules.cycle(3, -1) == 2);
        check("创造机：2 → 1", () -> PriorityRules.cycle(2, -1) == 1);
        check("创造机：1 → 0", () -> PriorityRules.cycle(1, -1) == 0);
        check("创造机：0 → 创造档(-1)", () -> PriorityRules.cycle(0, -1) == -1);
        check("创造机：创造档(-1) → 回到最高普通档 4",
                () -> PriorityRules.cycle(-1, -1) == 4);
        // 创造机整圈闭合：从 4 出发走 6 步应回到 4
        check("创造机：整圈 6 步闭合回 4", () -> {
            int t = 4;
            for (int i = 0; i < 6; i++) {
                t = PriorityRules.cycle(t, -1);
            }
            return t == 4;
        });

        // ---- 择优权重：这是「运行等级」影响产物选择的唯一规则 ----
        // 普通偏好：越贴近偏好档越优，且「≤偏好档」整体压过「>偏好档」。
        check("权重：普通偏好下精确命中最高",
                () -> TierHelper.selectionWeight(2, 2) > TierHelper.selectionWeight(1, 2));
        check("权重：≤偏好档 整体高于 >偏好档",
                () -> TierHelper.selectionWeight(0, 2) > TierHelper.selectionWeight(3, 2));
        check("权重：高于偏好档时越接近越优",
                () -> TierHelper.selectionWeight(3, 2) > TierHelper.selectionWeight(4, 2));
        check("权重：普通偏好下创造专用配方排最后",
                () -> TierHelper.selectionWeight(-1, 2) < TierHelper.selectionWeight(4, 2));
        // 创造偏好（未手动设置的创造机器）：创造专用最优，其余按高门槛优先
        check("权重：创造偏好下创造专用最优",
                () -> TierHelper.selectionWeight(-1, -1) > TierHelper.selectionWeight(4, -1));
        check("权重：创造偏好下高门槛优于低门槛",
                () -> TierHelper.selectionWeight(4, -1) > TierHelper.selectionWeight(1, -1));

        // ---- 择优端到端：机器真实等级 4，手动调到 2 → 同输入优先选 2 档 ----
        Config.clearAll();
        Config.addTier(rl("p:t1"), 1, SERVER);
        Config.addTier(rl("p:t2"), 2, SERVER);
        Config.addTier(rl("p:t4"), 4, SERVER);
        List<ResourceLocation> pool = ids("p:t1", "p:t2", "p:t4");
        check("运行等级=2 时，同输入优先选 2 档（而非最高的 4 档）",
                () -> TierHelper.pickBestIndexByTiers(4, 2, pool) == 1);
        check("未设置（择优档=4）时，仍选最高的 4 档",
                () -> TierHelper.pickBestIndexByTiers(4, 4, pool) == 2);

        // ---- 准入：运行等级=0 时带门槛配方全被拒 ----
        check("准入：运行等级0 拒 t1", () -> !TierHelper.isTierAllowed(0, 1));
        check("准入：运行等级0 放行无门槛", () -> TierHelper.isTierAllowed(0, 0));
        check("准入：运行等级2 拒 t4、放行 t2",
                () -> !TierHelper.isTierAllowed(2, 4) && TierHelper.isTierAllowed(2, 2));
        // 运行等级=0 时择优结果为 -1（一条都够不着）
        Config.clearAll();
        Config.addTier(rl("q:t1"), 1, SERVER);
        check("运行等级0：只有带门槛配方时择优为空",
                () -> TierHelper.pickBestIndexByTiers(0, 0, ids("q:t1")) == -1);

        // ---- 创造机器例外：准入恒为创造级（可做任何配方），但择优用运行等级 ----
        Config.clearAll();
        Config.addTier(rl("c:t1"), 1, SERVER);
        Config.addTier(rl("c:t3"), 3, SERVER);
        check("创造机器准入：仍可做高门槛配方（准入档=-1）",
                () -> TierHelper.isTierAllowed(-1, 3) && TierHelper.isTierAllowed(-1, -1));
        check("创造机器调到 1 后：择优优先选 1 档",
                () -> TierHelper.pickBestIndexByTiers(-1, 1, ids("c:t1", "c:t3")) == 0);
        check("创造机器未调时：择优仍优先高门槛",
                () -> TierHelper.pickBestIndexByTiers(-1, -1, ids("c:t1", "c:t3")) == 1);

        // ---- UNSET 哨兵不应与任何真实等级冲突 ----
        check("UNSET 不等于任何合法等级",
                () -> UNSET != -1 && UNSET != 0 && UNSET != 1 && UNSET != 2 && UNSET != 3 && UNSET != 4);
    }

    // =========================================================================
    //  「运行等级可调机器」名单回归
    // =========================================================================
    //
    //  名单是「扳手能调」与「护目镜显示」共用的唯一来源，所以它一旦漂移，
    //  症状只在游戏里出现（能调但不显示 / 显示但调不动）。这里把名单钉住。
    //
    //  刻意<b>不含</b> CMM 3.0 的分级鼓风机（encased_fan）：它的配方是 Create 的鼓风处理
    //  （splashing / haunting / blasting / smoking），本模组从未给这些配方装闸门
    //  （TierHelper 只覆盖 pressing / mixing / compacting / filling / deploying /
    //  cutting / crushing / milling），而 FanProcessingType.canProcess 拿不到
    //  「是哪台鼓风机」，无法按机器等级判定。给它一个调不动的控制等于骗人，
    //  所以先不入册 —— 这条断言就是防止将来有人「顺手补上」。
    // =========================================================================

    private static void machineRosterTests() {
        check("名单：6 台受支持机器", () -> MachineRoster.size() == 6);

        // ---- 应当入册 ----
        check("名单含 CMM 压机", () -> MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.mechanical.press.CMMMechanicalPressBlockEntity"));
        check("名单含 CMM 搅拌器", () -> MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.mechanical.mixer.CMMMechanicalMixerBlockEntity"));
        check("名单含 CMM 部署器", () -> MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.mechanical.deployer.CMMDeployerBlockEntity"));
        check("名单含 CMM 喷口", () -> MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.spout.CMMSpoutBlockEntity"));
        check("名单含 CMM 3.0 分级锯", () -> MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.mechanical.saw.CMMMechanicalSawBlockEntity"));
        check("名单含 CMM 3.0 破碎轮控制器", () -> MachineRoster.isSupported(
                MachineRoster.CMM_CRUSHING_WHEEL_CONTROLLER));

        // ---- 应当被排除 ----
        check("名单不含 CMM 3.0 分级鼓风机（本模组没有鼓风处理闸门）", () -> !MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.encased_fan.CMMEncasedFanBlockEntity"));
        check("名单不含 CMM 3.0 破碎轮「轮子」（数据在控制器上，轮子只是入口）",
                () -> !MachineRoster.isSupported(
                        "net.yxiao233.createmoremachines.api.content.crushing_wheel.CMMCrushingWheelBlockEntity"));
        check("名单不含旧 CMMM 包名的控制器（已退役）", () -> !MachineRoster.isSupported(
                "net.tomorrow325.createmoremoremachines.api.content.crushing_wheel.CMMCrushingWheelControllerBlockEntity"));
        check("名单不含原版 Create 破碎轮控制器（0 级，不可调）", () -> !MachineRoster.isSupported(
                "com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity"));
        check("名单不含 CMM 工作盆", () -> !MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.basin.CMMBasinBlockEntity"));
        check("名单不含 CMM 置物台", () -> !MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.depot.CMMDepotBlockEntity"));
        check("名单不含 CMM 流体罐", () -> !MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.fluid_tank.CMMFluidTankBlockEntity"));
        check("名单不含 CMM 蒸汽引擎", () -> !MachineRoster.isSupported(
                "net.yxiao233.createmoremachines.api.content.steam_engine.CMMSteamEngineBlockEntity"));
        check("名单不因前缀相似而误命中（前缀不是 isSupported 的判据）",
                () -> !MachineRoster.isSupported(
                        "net.yxiao233.createmoremachines.api.content.spout.CMMSpoutBlockEntityEvil"));

        // ---- 边界 ----
        check("名单：null → false", () -> !MachineRoster.isSupported((String) null));
        check("名单：空串 → false", () -> !MachineRoster.isSupported(""));
    }

    // =========================================================================
    //  「顺序即选择」机器的排序回归（锯 / 工作盆走的就是这条）
    // =========================================================================
    //
    //  这两台机器不是「返回一条」，而是拿到列表后用 list.get(0) 或
    //  list.get(recipeIndex) 取用。所以 rankIndicesForTier 返回的**顺序**就是行为本身：
    //  第 0 项必须是最高的那块，且同门槛时必须保持 Create 原有的相对顺序
    //  （工作盆靠它承载「配料数越多越优先」）。之前这层没有断言。
    // =========================================================================

    /** 把下标序列映射回 id 字符串，便于断言顺序。 */
    private static String order(int machineTier, List<ResourceLocation> list) {
        StringBuilder sb = new StringBuilder();
        for (int i : TierHelper.rankIndicesForTier(machineTier, list)) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(list.get(i).getPath());
        }
        return sb.toString();
    }

    private static void orderingTests() {
        // 关键：高的排到最前，低的仍在列表里（黄铜机器要做得了 t1，不能整条丢掉）。
        Config.clearAll();
        Config.addTier(rl("o:t1"), 1, SERVER);
        Config.addTier(rl("o:t2"), 2, SERVER);
        List<ResourceLocation> t12 = ids("o:t1", "o:t2");

        check("排序：下界级(2) 顺序为 t2,t1", () -> order(2, t12).equals("t2,t1"));
        check("排序：黄铜级(1) 只剩 t1（顺序 t1）", () -> order(1, t12).equals("t1"));
        check("排序：原版级(0) 两条都够不着 → 空序列", () -> order(0, t12).isEmpty());
        check("排序：创造级(-1) 顺序为 t2,t1（取最高常规级）", () -> order(-1, t12).equals("t2,t1"));

        // 稳定排序：同门槛必须保持原相对顺序（Create 自己的偏好编码在这里）。
        Config.clearAll();
        Config.addTier(rl("o:a"), 2, SERVER);
        Config.addTier(rl("o:b"), 2, SERVER);
        Config.addTier(rl("o:c"), 2, SERVER);
        check("排序稳定性：同门槛三连保持原顺序 a,b,c",
                () -> order(4, ids("o:a", "o:b", "o:c")).equals("a,b,c"));

        // 高低交错时，稳定性体现在「同门槛那几条的相对次序不变」。
        Config.clearAll();
        Config.addTier(rl("o:low1"), 1, SERVER);
        Config.addTier(rl("o:high1"), 3, SERVER);
        Config.addTier(rl("o:low2"), 1, SERVER);
        Config.addTier(rl("o:high2"), 3, SERVER);
        check("排序：高门槛整体在前且各自保持原序 → high1,high2,low1,low2",
                () -> order(4, ids("o:low1", "o:high1", "o:low2", "o:high2"))
                        .equals("high1,high2,low1,low2"));

        // 无门槛(0) 与带门槛混合：带门槛的在前，无门槛的仍保留可用。
        Config.clearAll();
        Config.addTier(rl("o:gated"), 2, SERVER);
        check("排序：带门槛在前、无门槛在后 → gated,plain",
                () -> order(2, ids("o:plain", "o:gated")).equals("gated,plain"));

        // null id（反查失败）按无门槛处理，不丢失。
        Config.clearAll();
        Config.addTier(rl("o:gated"), 2, SERVER);
        check("排序：null id 按无门槛保留且排在带门槛之后",
                () -> TierHelper.rankIndicesForTier(2,
                        java.util.Arrays.asList(null, rl("o:gated"))).size() == 2);
        check("排序：null id 时下标 1（带门槛）在前",
                () -> TierHelper.rankIndicesForTier(2,
                        java.util.Arrays.asList(null, rl("o:gated"))).get(0) == 1);

        // pickBestIndexForTier 必须与排序内核的第 0 项一致（两条路径不可发散）。
        Config.clearAll();
        Config.addTier(rl("o:p1"), 1, SERVER);
        Config.addTier(rl("o:p2"), 4, SERVER);
        List<ResourceLocation> p = ids("o:p1", "o:p2");
        check("一致性：pickBestIndex == rankIndices 的第 0 项",
                () -> TierHelper.pickBestIndexForTier(4, p) == 1
                        && TierHelper.rankIndicesForTier(4, p).get(0) == 1);
        check("一致性：够不着时 pickBestIndex 为 -1 且序列为空",
                () -> TierHelper.pickBestIndexForTier(0, p) == -1
                        && TierHelper.rankIndicesForTier(0, p).isEmpty());

        // 边界：null / 空列表。
        check("排序：null 列表 → 空序列", () -> TierHelper.rankIndicesForTier(4, null).isEmpty());
        check("排序：空列表 → 空序列", () -> TierHelper.rankIndicesForTier(4, ids()).isEmpty());
    }

    private static ResourceLocation rl(String id) {
        return ResourceLocation.parse(id);
    }

    private static void check(String name, BooleanSupplier condition) {
        boolean ok;
        try {
            ok = condition.getAsBoolean();
        } catch (Throwable t) {
            System.out.println("FAIL  " + name + "  (threw " + t + ")");
            failed++;
            return;
        }
        if (ok) {
            System.out.println("ok    " + name);
            passed++;
        } else {
            System.out.println("FAIL  " + name);
            failed++;
        }
    }
}
