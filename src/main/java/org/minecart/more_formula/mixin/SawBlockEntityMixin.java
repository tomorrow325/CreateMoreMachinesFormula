package org.minecart.more_formula.mixin;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 锯的门槛闸门 + 「同输入多配方择优」。
 *
 * <p>锯的选法与压机不同：{@code applyRecipe} 拿到的是 {@code getRecipes()} 返回的
 * <b>整个列表</b>，再用 {@code list.get(recipeIndex)} 取其中一条（已用字节码确认）。
 * 因此这里必须同时做两件事：滤掉等级不够的，并把等级最高的排到最前。
 *
 * <p><b>为什么要顺带把 recipeIndex 归零</b>：{@code recipeIndex} 会随方块实体写进
 * NBT（{@code write}/{@code read} 里的 {@code "RecipeIndex"}）。原版只在
 * {@code recipeIndex >= list.size()} 时才重置它。我们改变了列表的内容与顺序，
 * 若沿用旧存档里存下的下标，就可能取到与之前不同的配方 —— 归零能保证
 * {@code get(0)} 恒为「够得着的最高等级」那条，行为确定且可预期。
 *
 * <p>{@code getRecipes} 是 start/applyRecipe 共用的唯一配方池来源；全部被过滤时
 * Create 按「无匹配配方」处理，物品在短倒计时后原样退回 —— 无损。
 *
 * <p>原版锯是 0 级机器：带门槛的 cutting/stonecutting 配方不允许执行。
 * CMMM 的分级锯继承自 {@code SawBlockEntity}，同一个 mixin 自动作用于它；
 * 等级识别走 {@link TierHelper#getMachineTier} 的反射桥接（CMMM 可选依赖，
 * 未安装时分级锯不存在，本 mixin 对原版锯的行为不变）。
 */
@Mixin(SawBlockEntity.class)
public abstract class SawBlockEntityMixin {

    @Shadow
    private int recipeIndex;

    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gateSawRecipes(CallbackInfoReturnable<List<RecipeHolder<? extends Recipe<?>>>> cir) {
        List<RecipeHolder<? extends Recipe<?>>> recipes = cir.getReturnValue();
        if (recipes == null || recipes.isEmpty()) {
            return;
        }
        BlockEntity machine = (BlockEntity) (Object) this;

        // 1) 取出每个候选的 id，交给 TierHelper 的统一内核：
        //    按「准入等级」滤掉够不着的 + 按「运行等级」偏好降序稳定排序。
        List<ResourceLocation> ids = new ArrayList<>(recipes.size());
        for (RecipeHolder<? extends Recipe<?>> holder : recipes) {
            ids.add(holder == null ? null : holder.id());
        }
        List<Integer> order = TierHelper.rankIndicesForTiers(
                TierHelper.getAdmissionTier(machine), TierHelper.getSelectionTier(machine), ids);
        if (order.isEmpty()) {
            cir.setReturnValue(List.of());
            return;
        }
        List<RecipeHolder<? extends Recipe<?>>> ranked = new ArrayList<>(order.size());
        for (int i : order) {
            ranked.add(recipes.get(i));
        }

        // 2) 列表内容/顺序已变，旧下标不再有意义 —— 归零以确定性取到最高等级那条。
        this.recipeIndex = 0;

        cir.setReturnValue(ranked);
    }
}
