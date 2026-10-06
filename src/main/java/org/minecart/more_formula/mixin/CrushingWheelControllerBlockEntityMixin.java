package org.minecart.more_formula.mixin;

import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 原版破碎轮控制器是 0 级机器：带门槛的 crushing/milling 配方不允许在它上面执行
 * （与原版压机/喷口拒绝门槛配方的语义一致）。
 *
 * <p>拦截点选择 {@code findRecipe} 的语义后果要在文档里讲清楚：破碎轮控制器对
 * 「无配方」的原生行为是倒计时结束后清空库存 —— 即带门槛的物品投给不合格的破碎轮
 * 会被研磨掉，而不是退回。这与投进任意不可研磨物品的原版表现相同；压力板/喷口那类
 * 「物品原样等待」的无损语义在破碎轮上不存在。
 *
 * <p>CMMM 的分级破碎轮控制器整体覆写了 {@code findRecipe}（不调用 super），
 * 因此本 mixin 只作用于原版控制器；CMMM 控制器由
 * {@code CMMMCrushingWheelControllerBlockEntityMixin} 单独拦截。
 */
@Mixin(CrushingWheelControllerBlockEntity.class)
public abstract class CrushingWheelControllerBlockEntityMixin {
    @Inject(method = "findRecipe", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gateCrushing(
            CallbackInfoReturnable<Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>>> cir) {
        Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> result = cir.getReturnValue();
        if (result.isPresent() && !TierHelper.isAllowed((BlockEntity) (Object) this, result.get().id())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
