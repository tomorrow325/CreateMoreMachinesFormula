package org.minecart.more_formula.compat.mekanicalcreate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public final class MekanicalCreateSpeedConfig {
    private static final int MIN_MULTIPLIER = 1;
    private static final int MAX_MULTIPLIER = 64;
    private static final int[] TIERS = {1, 2, 3, 4, -1};
    private static final String[] TIER_NAMES = {"brass", "netherite", "end", "beyond", "creative"};

    public static final ModConfigSpec SPEC;
    private static final Map<MekanicalCreateRecipeGate.ModuleKind, Map<Integer, ModConfigSpec.IntValue>> VALUES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        EnumMap<MekanicalCreateRecipeGate.ModuleKind, Map<Integer, ModConfigSpec.IntValue>> values =
                new EnumMap<>(MekanicalCreateRecipeGate.ModuleKind.class);
        builder.comment("Speed multipliers for tiered CMM modules in Mekanical-Create.")
                .push("mekanicalcreate");
        defineKind(builder, values, MekanicalCreateRecipeGate.ModuleKind.PRESS, "press");
        defineKind(builder, values, MekanicalCreateRecipeGate.ModuleKind.DEPLOYER, "deployer");
        defineKind(builder, values, MekanicalCreateRecipeGate.ModuleKind.MIXER, "mixer");
        defineKind(builder, values, MekanicalCreateRecipeGate.ModuleKind.SPOUT, "spout");
        // CMMM（可选依赖）的分级破碎轮/分级锯作为模块时同样吃按档倍率。
        defineKind(builder, values, MekanicalCreateRecipeGate.ModuleKind.CRUSHING_WHEEL, "crushing_wheel");
        defineKind(builder, values, MekanicalCreateRecipeGate.ModuleKind.SAW, "saw");
        builder.pop();
        SPEC = builder.build();
        VALUES = Map.copyOf(values);
    }

    private MekanicalCreateSpeedConfig() {
    }

    public static int getMultiplier(ItemStack module) {
        if (module.isEmpty()) {
            return 1;
        }
        ResourceLocation moduleId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(module.getItem());
        return getMultiplier(moduleId);
    }

    public static int getMultiplier(ResourceLocation moduleId) {
        return getMultiplier(MekanicalCreateRecipeGate.getModuleKind(moduleId),
                MekanicalCreateRecipeGate.getModuleTier(moduleId));
    }

    public static int getMultiplier(MekanicalCreateRecipeGate.ModuleKind kind, int tier) {
        ModConfigSpec.IntValue value = VALUES.getOrDefault(kind, Map.of()).get(tier);
        return value == null ? 1 : value.get();
    }

    public static int getHighestMultiplier(Iterable<Integer> multipliers) {
        int highest = 1;
        for (int multiplier : multipliers) {
            highest = Math.max(highest, clampMultiplier(multiplier));
        }
        return highest;
    }

    public static int configuredTierCount(MekanicalCreateRecipeGate.ModuleKind kind) {
        return VALUES.getOrDefault(kind, Map.of()).size();
    }

    public static int defaultMultiplier(int tier) {
        return switch (tier) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4, -1 -> 4;
            default -> 1;
        };
    }

    public static int scaleParallelCount(int baseCount, int multiplier) {
        if (baseCount <= 0) {
            return baseCount;
        }
        long scaled = (long) baseCount * clampMultiplier(multiplier);
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    public static long scaleWorkBudget(long baseWork, int multiplier) {
        if (baseWork <= 0) {
            return baseWork;
        }
        try {
            return Math.multiplyExact(baseWork, clampMultiplier(multiplier));
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public static int clampMultiplier(int multiplier) {
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
    }

    private static void defineKind(
            ModConfigSpec.Builder builder,
            EnumMap<MekanicalCreateRecipeGate.ModuleKind,
                    Map<Integer, ModConfigSpec.IntValue>> values,
            MekanicalCreateRecipeGate.ModuleKind kind,
            String path) {
        builder.push(path);
        Map<Integer, ModConfigSpec.IntValue> tiers = new java.util.HashMap<>();
        for (int index = 0; index < TIERS.length; index++) {
            int tier = TIERS[index];
            tiers.put(tier, builder.comment("Processing multiplier for CMM "
                            + TIER_NAMES[index] + " " + path + " modules.")
                    .defineInRange(TIER_NAMES[index], defaultMultiplier(tier),
                            MIN_MULTIPLIER, MAX_MULTIPLIER));
        }
        builder.pop();
        values.put(kind, Map.copyOf(tiers));
    }
}
