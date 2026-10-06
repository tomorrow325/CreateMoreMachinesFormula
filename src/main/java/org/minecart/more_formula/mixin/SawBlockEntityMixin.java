package org.minecart.more_formula.mixin;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 拦截锯的配方池。{@code getRecipes} 是 start/applyRecipe 共用的唯一配方选择点，
 * 从返回值里过滤掉不允许的配方即可同时覆盖两条路径；全部被过滤时 Create 按
 * 「无匹配配方」处理，物品在短倒计时后原样退回 —— 无损。
 *
 * <p>原版锯是 0 级机器：带门槛的 cutting/stonecutting 配方不允许执行。
 * CMMM 的分级锯继承自 {@code SawBlockEntity}，同一个 mixin 自动作用于它；
 * 等级识别走 {@link TierHelper#getMachineTier} 的反射桥接（CMMM 可选依赖，
 * 未安装时分级锯不存在，本 mixin 对原版锯的行为不变）。
 */
@Mixin(SawBlockEntity.class)
public abstract class SawBlockEntityMixin {
    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gateSawRecipes(CallbackInfoReturnable<List<RecipeHolder<? extends Recipe<?>>>> cir) {
        List<RecipeHolder<? extends Recipe<?>>> recipes = cir.getReturnValue();
        if (recipes == null || recipes.isEmpty()) {
            return;
        }
        BlockEntity machine = (BlockEntity) (Object) this;
        List<RecipeHolder<? extends Recipe<?>>> gated = new ArrayList<>(recipes.size());
        for (RecipeHolder<? extends Recipe<?>> holder : recipes) {
            if (TierHelper.isAllowed(machine, holder.id())) {
                gated.add(holder);
            }
        }
        cir.setReturnValue(gated);
    }
}
