package org.minecart.more_formula;

// Config 逻辑的离线回归测试。
//
// 为什么需要它：Config 是纯静态、不依赖 Minecraft 运行时的类（只用到 ResourceLocation），
// 因此可以直接在 JVM 里跑，把「修没修好」变成可执行的断言，而不是靠肉眼看代码。
// 覆盖本次修复中的三条：热重载不累积（A）、前缀门槛进 knownTiers（C）、
// 双 map 合并后的优先级（D）。

import net.minecraft.resources.ResourceLocation;
import org.minecart.more_formula.compat.createmoremoremachines.CMMMTierBridge;
import org.minecart.more_formula.compat.mekanicalcreate.MekanicalCreateRecipeGate;
import org.minecart.more_formula.compat.mekanicalcreate.MekanicalCreateSpeedConfig;
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

        // ---- CreateMoreMoreMachines（可选）：CMM 等级值 → 本模组等级值的平移 ----
        // CMMM 复用 CMM 的 CMMTier（brass=2/netherite=3/end=4/beyond=5/creative=-1），
        // 与 CMM 机器共用同一映射，不能因接入新机器而漂移。
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

        // ---- 反射桥接类在未连接运行时也应安全加载并对 null 返回 0 级 ----
        check("CMMM 桥接对 null 机器返回 0 级", () -> CMMMTierBridge.getMachineTier(null) == 0);

        System.out.println();
        System.out.println("PASSED=" + passed + " FAILED=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
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
