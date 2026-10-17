package org.minecart.more_formula.mixin;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 原版破碎轮控制器是 0 级机器：带门槛的 crushing/milling 配方不允许在它上面执行
 * （与原版压机/喷口拒绝门槛配方的语义一致）。
 *
 * <p><b>同时负责「同输入多配方择优」</b>：Create 的 {@code AllRecipeTypes.find}
 * 只回「注册顺序里第一条」匹配配方，与门槛无关。同一输入写了 t1/t2 两条 crushing 时，
 * 先注册的 t1 永远先命中 —— 于是下界破碎轮也会执行黄铜配方。这里改成先取该输入下
 * <b>所有</b>匹配配方，滤掉等级不够的，再取门槛最高的一条。
 *
 * <p>顺序保持 Create 原生优先级：先看 crushing，没有再回退 milling；两者合并成
 * 一个候选池后一起择优。若 crushing 与 milling 各有一条同门槛的配方，按原版
 * 「crushing 优先」取 crushing（{@link TierHelper#pickBestAllowed} 并列时取候选
 * 顺序里的第一条，这里 crushing 先入池）。
 *
 * <p>拦截点选择 {@code findRecipe} 的语义后果要在文档里讲清楚：破碎轮控制器对
 * 「无配方」的原生行为是倒计时结束后清空库存 —— 即带门槛的物品投给不合格的破碎轮
 * 会被研磨掉，而不是退回。这与投进任意不可研磨物品的原版表现相同；压力板/喷口那类
 * 「物品原样等待」的无损语义在破碎轮上不存在。
 *
 * <p>CMMM 的分级破碎轮控制器整体覆写了 {@code findRecipe}（不调用 super），
 * 因此本 mixin 只作用于原版控制器；CMMM 控制器由
 * {@code CMMMCrushingWheelControllerBlockEntityMixin} 单独拦截（同一套择优逻辑）。
 */
@Mixin(CrushingWheelControllerBlockEntity.class)
public abstract class CrushingWheelControllerBlockEntityMixin {

    @Shadow
    private RecipeWrapper wrapper;

    @Inject(method = "findRecipe", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gateCrushing(
            CallbackInfoReturnable<Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>>> cir) {
        BlockEntity machine = (BlockEntity) (Object) this;
        Level level = machine.getLevel();
        if (level == null) {
            return;
        }
        List<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> candidates =
                TierHelper.crushingCandidates(level, wrapper);
        if (candidates.isEmpty()) {
            // 本来就没有任何匹配配方：保持原返回值（含「原版判定为空」的情况）。
            return;
        }
        cir.setReturnValue(TierHelper.pickBestAllowed(machine, candidates));
    }
}
