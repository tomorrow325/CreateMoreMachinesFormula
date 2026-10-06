package org.minecart.more_formula.compat.createmoremoremachines.mixin;

import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.tomorrow325.createmoremoremachines.api.content.crushing_wheel.CMMCrushingWheelControllerBlockEntity;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 按配方门槛拦截 CMMM 的分级破碎轮控制器（该控制器同时承载 crushing 与
 * milling 回退查找，一处拦截两条通道都覆盖）。
 *
 * <p>语义与原版破碎轮一致：{@code findRecipe} 返回空时，Create 的私有
 * {@code applyRecipe} 走 else 分支清空库存 —— 也就是说，等级不够的机器把带门槛的
 * 配方视为「无配方」，物品在倒计时结束后被研磨掉。这是破碎轮表达「处理不了」的
 * 原生方式（原版对无配方物品同样如此），文档中已明确标注。
 */
@Mixin(CMMCrushingWheelControllerBlockEntity.class)
public abstract class CMMMCrushingWheelControllerBlockEntityMixin {
    @Inject(method = "findRecipe", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gateCrushing(
            CallbackInfoReturnable<Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>>> cir) {
        Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> result = cir.getReturnValue();
        if (result.isPresent() && !TierHelper.isAllowed((BlockEntity) (Object) this, result.get().id())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
