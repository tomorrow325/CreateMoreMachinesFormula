package org.minecart.more_formula.compat.mekanicalcreate.mixin;

import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.yxiao233.createmoremachines.api.registry.BuiltInAdvancedMachineTypes;
import org.minecart.more_formula.Config;
import org.minecart.more_formula.compat.mekanicalcreate.MekanicalCreateRecipeGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Mixin(targets = "io.github.langqi99.mekanicalcreate.content.SimulationRecipeResolver", remap = false)
public abstract class SimulationRecipeResolverMixin {
    @Invoker(value = "collectCandidates", remap = false)
    private static List<?> moreFormula$collectCandidates(
            Level level, ItemStack module, ItemStack condition, boolean allowFluidProcessing) {
        throw new AssertionError();
    }

    @Invoker(value = "appendDisplayRecipes", remap = false)
    private static void moreFormula$appendDisplayRecipes(
            List<?> displays, List<?> candidates, ItemStack module, ItemStack condition) {
        throw new AssertionError();
    }

    @Inject(
            method = "getDisplayRecipes(Lnet/minecraft/world/level/Level;Z)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void moreFormula$showMinimumTierModules(
            Level level, boolean allowFluidProcessing,
            CallbackInfoReturnable<List<?>> cir) {
        List<Object> displays = new ArrayList<>();
        Set<ResourceLocation> displayIds = new LinkedHashSet<>();

        for (Object display : cir.getReturnValue()) {
            ResourceLocation displayId = displayId(display);
            ResourceLocation sourceId = MekanicalCreateRecipeGate.sourceRecipeId(displayId);
            int requiredTier = Config.getRequiredTier(sourceId);
            int moduleTier = MekanicalCreateRecipeGate.getModuleTier(displayModule(display));
            if ((requiredTier == 0 || MekanicalCreateRecipeGate.isMinimumTier(moduleTier, requiredTier))
                    && displayIds.add(displayId)) {
                displays.add(display);
            }
        }

        BuiltInAdvancedMachineTypes.AdvancedMachineType<?>[] machineTypes = allowFluidProcessing
                ? new BuiltInAdvancedMachineTypes.AdvancedMachineType[]{
                        BuiltInAdvancedMachineTypes.PRESS,
                        BuiltInAdvancedMachineTypes.DEPLOYER,
                        BuiltInAdvancedMachineTypes.MIXER,
                        BuiltInAdvancedMachineTypes.SPOUT}
                : new BuiltInAdvancedMachineTypes.AdvancedMachineType[]{
                        BuiltInAdvancedMachineTypes.PRESS,
                        BuiltInAdvancedMachineTypes.DEPLOYER};

        for (int tier : new int[]{1, 2, 3, 4, Config.CREATIVE_TIER}) {
            ResourceLocation tierId = ResourceLocation.fromNamespaceAndPath(
                    "createmoremachines", tierName(tier));
            for (BuiltInAdvancedMachineTypes.AdvancedMachineType<?> machineType : machineTypes) {
                BlockEntry<?> machine = machineType.getAdvancedMechanicals().get(tierId);
                if (machine == null) {
                    continue;
                }
                ItemStack module = new ItemStack(machine.get().asItem());
                List<?> candidates = moreFormula$collectCandidates(
                        level, module, ItemStack.EMPTY, allowFluidProcessing);
                List<?> minimumTierCandidates = MekanicalCreateRecipeGate
                        .filterMinimumTierCandidates(module, candidates);
                if (!minimumTierCandidates.isEmpty()) {
                    List<Object> added = new ArrayList<>();
                    moreFormula$appendDisplayRecipes(added, minimumTierCandidates, module, ItemStack.EMPTY);
                    for (Object display : added) {
                        if (displayIds.add(displayId(display))) {
                            displays.add(display);
                        }
                    }
                }
            }
        }

        cir.setReturnValue(List.copyOf(displays));
    }

    @Inject(
            method = "isSupportedModule(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Z)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void moreFormula$allowTieredModules(
            Level level, ItemStack module, boolean allowFluidProcessing,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && MekanicalCreateRecipeGate.isSupportedModule(
                module, allowFluidProcessing)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "buildCandidates(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void moreFormula$filterTieredCandidates(
            Level level, ItemStack module, ItemStack condition,
            boolean allowFluidProcessing, CallbackInfoReturnable<List<?>> cir) {
        if (!module.isEmpty()) {
            cir.setReturnValue(MekanicalCreateRecipeGate.filterCandidates(module, cir.getReturnValue()));
        }
    }

    @Redirect(
            method = "buildCandidates(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"),
            require = 1, remap = false)
    private static boolean moreFormula$matchCmmModuleType(
            ItemStack module, Item createModule) {
        if (module.is(createModule)) {
            return true;
        }
        return MekanicalCreateRecipeGate.matchesCreateModule(module, createModule, true);
    }

    @Redirect(
            method = "addSequenced(Ljava/util/List;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeManager;Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 1, remap = false)
    private static boolean moreFormula$matchCmmSequencedModule(
            ItemStack selectedModule, ItemStack stepModule) {
        return ItemStack.isSameItemSameComponents(selectedModule, stepModule)
                || MekanicalCreateRecipeGate.matchesSequenceModule(selectedModule, stepModule);
    }

    private static ResourceLocation displayId(Object display) {
        return (ResourceLocation) invokeAccessor(display, "id");
    }

    private static ItemStack displayModule(Object display) {
        return (ItemStack) invokeAccessor(display, "module");
    }

    private static Object invokeAccessor(Object target, String name) {
        try {
            Method accessor = target.getClass().getMethod(name);
            return accessor.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to read Mekanical-Create JEI display", exception);
        }
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case Config.CREATIVE_TIER -> "creative";
            case 1 -> "brass";
            case 2 -> "netherite";
            case 3 -> "end";
            case 4 -> "beyond";
            default -> "";
        };
    }
}
