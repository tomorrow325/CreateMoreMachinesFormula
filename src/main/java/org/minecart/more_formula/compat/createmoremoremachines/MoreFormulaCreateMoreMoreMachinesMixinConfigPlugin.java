package org.minecart.more_formula.compat.createmoremoremachines;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Loads CreateMoreMoreMachines mixins only when the optional mod is installed. */
public class MoreFormulaCreateMoreMoreMachinesMixinConfigPlugin implements IMixinConfigPlugin {
    private boolean cmmmLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        cmmmLoaded = FMLLoader.getLoadingModList() != null
                && FMLLoader.getLoadingModList().getModFileById("createmoremoremachines") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return cmmmLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
