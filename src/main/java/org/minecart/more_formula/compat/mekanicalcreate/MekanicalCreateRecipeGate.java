package org.minecart.more_formula.compat.mekanicalcreate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.minecart.more_formula.Config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MekanicalCreateRecipeGate {
    private static final String CMM_NAMESPACE = "createmoremachines";
    /** 可选依赖 CreateMoreMoreMachines 的命名空间：分级机器与 CMM 共享 CMMTier 实例，同语义识别。 */
    private static final String CMMM_NAMESPACE = "createmoremoremachines";
    private static final String CANDIDATE_SUFFIX = "/mekanicalcreate_";
    private static volatile Method candidateIdAccessor;

    public enum ModuleKind {
        NONE,
        DEPLOYER,
        PRESS,
        MIXER,
        SPOUT,
        CRUSHING_WHEEL,
        SAW
    }

    private MekanicalCreateRecipeGate() {
    }

    public static int getModuleTier(ItemStack module) {
        return module.isEmpty() ? 0 : getModuleTier(BuiltInRegistries.ITEM.getKey(module.getItem()));
    }

    public static ModuleKind getModuleKind(ResourceLocation moduleId) {
        if (moduleId == null || !isTieredModuleNamespace(moduleId.getNamespace())) {
            return ModuleKind.NONE;
        }
        String path = moduleId.getPath();
        if (hasTieredMachineName(path, "deployer")) {
            return ModuleKind.DEPLOYER;
        }
        if (hasTieredMachineName(path, "mechanical_press")) {
            return ModuleKind.PRESS;
        }
        if (hasTieredMachineName(path, "mechanical_mixer")) {
            return ModuleKind.MIXER;
        }
        if (hasTieredMachineName(path, "spout")) {
            return ModuleKind.SPOUT;
        }
        if (hasTieredMachineName(path, "crushing_wheel")) {
            return ModuleKind.CRUSHING_WHEEL;
        }
        if (hasTieredMachineName(path, "mechanical_saw")) {
            return ModuleKind.SAW;
        }
        return ModuleKind.NONE;
    }

    public static boolean isSupportedModule(ItemStack module, boolean allowFluidProcessing) {
        return !module.isEmpty() && isSupportedModule(
                BuiltInRegistries.ITEM.getKey(module.getItem()), allowFluidProcessing);
    }

    public static boolean isSupportedModule(ResourceLocation moduleId, boolean allowFluidProcessing) {
        ModuleKind kind = getModuleKind(moduleId);
        return kind == ModuleKind.DEPLOYER || kind == ModuleKind.PRESS
                || kind == ModuleKind.CRUSHING_WHEEL || kind == ModuleKind.SAW
                || allowFluidProcessing && (kind == ModuleKind.MIXER || kind == ModuleKind.SPOUT);
    }

    public static boolean matchesCreateModule(ItemStack module, Item createModule,
                                              boolean allowFluidProcessing) {
        if (module.isEmpty()) {
            return false;
        }
        ResourceLocation moduleId = BuiltInRegistries.ITEM.getKey(module.getItem());
        ResourceLocation createModuleId = BuiltInRegistries.ITEM.getKey(createModule);
        return matchesCreateModule(moduleId, createModuleId, allowFluidProcessing);
    }

    public static boolean matchesCreateModule(ResourceLocation moduleId,
                                              ResourceLocation createModuleId,
                                              boolean allowFluidProcessing) {
        if (createModuleId == null || !"create".equals(createModuleId.getNamespace())) {
            return false;
        }
        ModuleKind kind = getModuleKind(moduleId);
        if (!allowFluidProcessing && (kind == ModuleKind.MIXER || kind == ModuleKind.SPOUT)) {
            return false;
        }
        String path = createModuleId.getPath();
        return switch (kind) {
            case DEPLOYER -> path.equals("deployer");
            case PRESS -> path.equals("mechanical_press");
            case MIXER -> path.equals("mechanical_mixer");
            case SPOUT -> path.equals("spout");
            case CRUSHING_WHEEL -> path.equals("crushing_wheel");
            case SAW -> path.equals("mechanical_saw");
            case NONE -> false;
        };
    }

    public static boolean matchesSequenceModule(ItemStack selectedModule, ItemStack sequenceModule) {
        if (selectedModule.isEmpty() || sequenceModule.isEmpty()) {
            return false;
        }
        return matchesSequenceModule(
                BuiltInRegistries.ITEM.getKey(selectedModule.getItem()),
                BuiltInRegistries.ITEM.getKey(sequenceModule.getItem()));
    }

    public static boolean matchesSequenceModule(ResourceLocation selectedModuleId,
                                                ResourceLocation sequenceModuleId) {
        ModuleKind selectedKind = getModuleKind(selectedModuleId);
        if (!"create".equals(sequenceModuleId.getNamespace())) {
            return false;
        }
        String path = sequenceModuleId.getPath();
        return switch (selectedKind) {
            case DEPLOYER -> path.equals("deployer");
            case PRESS -> path.equals("mechanical_press");
            case SPOUT -> path.equals("spout");
            case SAW -> path.equals("mechanical_saw");
            // 破碎不是序列装配步骤；混合/未知机器也不参与序列加成映射。
            case CRUSHING_WHEEL, MIXER, NONE -> false;
        };
    }

    private static boolean hasTieredMachineName(String path, String machineName) {
        return path.equals(machineName) || path.equals("creative_" + machineName)
                || path.equals("brass_" + machineName)
                || path.equals("netherite_" + machineName)
                || path.equals("end_" + machineName)
                || path.equals("beyond_" + machineName);
    }

    /** CMM 本体与可选依赖 CMMM 的分级机器都挂在这两个命名空间下。 */
    private static boolean isTieredModuleNamespace(String namespace) {
        return CMM_NAMESPACE.equals(namespace) || CMMM_NAMESPACE.equals(namespace);
    }

    public static int getModuleTier(ResourceLocation moduleId) {
        if (moduleId == null || !isTieredModuleNamespace(moduleId.getNamespace())) {
            return 0;
        }
        String path = moduleId.getPath();
        if (path.startsWith("creative_")) {
            return Config.CREATIVE_TIER;
        }
        if (path.startsWith("brass_")) {
            return 1;
        }
        if (path.startsWith("netherite_")) {
            return 2;
        }
        if (path.startsWith("end_")) {
            return 3;
        }
        if (path.startsWith("beyond_")) {
            return 4;
        }
        return 0;
    }

    public static boolean isAllowed(int moduleTier, int requiredTier) {
        if (requiredTier == Config.CREATIVE_TIER) {
            return moduleTier == Config.CREATIVE_TIER;
        }
        return requiredTier <= 0
                || moduleTier == Config.CREATIVE_TIER
                || moduleTier >= requiredTier;
    }

    public static boolean isMinimumTier(int moduleTier, int requiredTier) {
        if (requiredTier == Config.CREATIVE_TIER) {
            return moduleTier == Config.CREATIVE_TIER;
        }
        return requiredTier >= Config.MIN_TIER && requiredTier <= Config.MAX_TIER
                && moduleTier == requiredTier;
    }

    public static ResourceLocation sourceRecipeId(ResourceLocation candidateId) {
        if (candidateId == null) {
            return null;
        }
        int suffixIndex = candidateId.getPath().indexOf(CANDIDATE_SUFFIX);
        if (suffixIndex < 0) {
            return candidateId;
        }
        String sourcePath = candidateId.getPath().substring(0, suffixIndex);
        if (sourcePath.isEmpty()) {
            return candidateId;
        }
        return ResourceLocation.fromNamespaceAndPath(candidateId.getNamespace(), sourcePath);
    }

    public static List<?> filterCandidates(ItemStack module, List<?> candidates) {
        int moduleTier = getModuleTier(module);
        List<Object> allowed = new ArrayList<>(candidates.size());
        for (Object candidate : candidates) {
            ResourceLocation recipeId = sourceRecipeId(getCandidateId(candidate));
            if (isAllowed(moduleTier, Config.getRequiredTier(recipeId))) {
                allowed.add(candidate);
            }
        }
        return List.copyOf(allowed);
    }

    public static List<?> filterMinimumTierCandidates(ItemStack module, List<?> candidates) {
        int moduleTier = getModuleTier(module);
        List<Object> minimumTier = new ArrayList<>(candidates.size());
        for (Object candidate : candidates) {
            ResourceLocation recipeId = sourceRecipeId(getCandidateId(candidate));
            if (isMinimumTier(moduleTier, Config.getRequiredTier(recipeId))) {
                minimumTier.add(candidate);
            }
        }
        return List.copyOf(minimumTier);
    }

    private static ResourceLocation getCandidateId(Object candidate) {
        try {
            Method accessor = candidateIdAccessor;
            if (accessor == null) {
                accessor = candidate.getClass().getDeclaredMethod("id");
                accessor.setAccessible(true);
                candidateIdAccessor = accessor;
            }
            return (ResourceLocation) accessor.invoke(candidate);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to read Mekanical-Create candidate id", exception);
        }
    }
}
