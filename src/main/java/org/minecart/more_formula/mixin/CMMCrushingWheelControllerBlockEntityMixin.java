package org.minecart.more_formula.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.yxiao233.createmoremachines.api.content.crushing_wheel.CMMCrushingWheelControllerBlockEntity;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;

/**
 * CMM 3.0 分级破碎轮控制器的门槛闸门 + 「同输入多配方择优」。
 *
 * <p><b>为什么改 {@code find} 的返回值，而不是在 {@code findRecipe} RETURN 处整体替换</b>：
 * 3.0 的 {@code findRecipe} 在拿到配方后还有一段后处理 —— 按等级取 speed 倍率
 * （{@code tier.getSpecial(CRUSHING_WHEEL, "speed")}），倍率 &gt; 1 时把原配方包进一个
 * 内部子类缩短时长再返回；产量放大则由 {@code BigItemStackHandler} 的扩容库存承担。
 * 若在 RETURN 处直接 {@code setReturnValue}，时长缩短会被整段跳过。
 *
 * <p>因此这里把注入点上移到控制器内部那次 {@code AllRecipeTypes.find}：只替换
 * 「它找到哪条配方」，让 CMM 自己的放大逻辑照常作用在我们选出的配方上。
 *
 * <p>{@code findRecipe} 里有 <b>两处</b> {@code find}（先 CRUSHING、没有再 MILLING 回退），
 * 两者描述符相同，因此这一个 {@code @ModifyExpressionValue} 会同时作用于两处 ——
 * 这正是我们要的：无论命中哪一处，返回的都是「该输入下这台机器够得着的最高门槛配方」。
 * 若一条都不够，则返回空，控制器按「无配方」处理 —— Create 的私有 {@code applyRecipe}
 * 走 else 分支清空库存，物品在倒计时结束后被研磨掉。这是破碎轮表达「处理不了」的
 * 原生方式。
 */
@Mixin(CMMCrushingWheelControllerBlockEntity.class)
public abstract class CMMCrushingWheelControllerBlockEntityMixin {

    @Shadow
    private RecipeWrapper createmoremachines$recipeWrapper;

    /**
     * 把控制器内部那次 {@code AllRecipeTypes.find(...)} 的结果替换为
     * 「crushing/milling 候选中，这台机器够得着的最高门槛」那条。
     *
     * <p>CMM 自己的优先级（先 crushing 后 milling）由它原有的控制流保证：
     * 只有当 crushing 那次调用返回空时，才会走到 milling 那次调用。
     */
    @ModifyExpressionValue(
            method = "findRecipe",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/AllRecipeTypes;find(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> moreFormula$pickBest(
            Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> original) {
        BlockEntity machine = (BlockEntity) (Object) this;
        Level level = machine.getLevel();
        if (level == null || createmoremachines$recipeWrapper == null) {
            return original;
        }
        List<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> candidates =
                TierHelper.crushingCandidates(level, createmoremachines$recipeWrapper);
        if (candidates.isEmpty()) {
            // 该输入下本来就没有任何匹配配方：保持 CMM 原本的判定（空）。
            return original;
        }
        return TierHelper.pickBestAllowed(machine, candidates);
    }
}
