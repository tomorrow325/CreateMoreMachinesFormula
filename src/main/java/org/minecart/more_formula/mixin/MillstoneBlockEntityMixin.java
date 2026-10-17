package org.minecart.more_formula.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * 原版磨石是 0 级机器：带门槛的 milling 配方不允许在它上面执行
 * （与原版压机/喷口拒绝门槛配方的语义一致）。
 *
 * <p>磨石有两处需要处理，因为它的「准入」和「执行哪条」是分开的：
 * <ol>
 *   <li>{@code canProcess} —— 插入闸门（{@code isItemValid} 调用）。返回 false 时
 *       漏斗/传送带都塞不进来，物品留在来源处，<b>无损</b>。这里按「该输入所有匹配
 *       配方里的最高门槛」判定：只要存在一条它做不了的，就不让进（宁严勿漏，
 *       因为进去之后由不得我们决定做哪条）。</li>
 *   <li>{@code process} 内的 {@code AllRecipeTypes.find} —— <b>真正决定执行哪条</b>。
 *       它同样是 {@code findFirst()}（按注册顺序），所以同一输入写了 t1/t2 两条
 *       milling 时也会执行低的。这里用 {@link ModifyExpressionValue} 把这个调用
 *       的返回值替换成「该输入下所有匹配配方中，这台机器够得着的门槛最高者」。</li>
 * </ol>
 *
 * <p>注意：本模组与 CMM/CMMM <b>都没有分级磨石</b>（CMM 2.7 的机器清单里没有磨石，
 * CMMM 只发布分级破碎轮与分级锯），所以磨石恒为 0 级。上面第 2 条对原版磨石的效果
 * 是「带门槛的配方一律拒绝」（因为 0 级机器够不着任何门槛配方），与旧行为一致；
 * 它的价值在于：一旦将来出现分级磨石，择优逻辑无需再改。
 */
@Mixin(MillstoneBlockEntity.class)
public abstract class MillstoneBlockEntityMixin {

    @Shadow
    public ItemStackHandler inputInv;

    @Inject(method = "canProcess", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gateMilling(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null) {
            return;
        }
        // 复刻 canProcess 自己的探测方式：单格测试库存 + RecipeWrapper。
        ItemStackHandler tester = new ItemStackHandler(1);
        tester.setStackInSlot(0, stack);
        RecipeWrapper input = new RecipeWrapper(tester);
        BlockEntity machine = (BlockEntity) (Object) this;

        List<RecipeHolder<com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe>> all =
                TierHelper.millingRecipes(level, input);
        // 取「门槛最高」的那条当准入依据 —— 比较用 selectionRank，但真正拿去判定的
        // 必须是**原始门槛值**（创造级的 rank 是 MAX_VALUE，不能直接喂给 isTierAllowed，
        // 否则会变成要求 machineTier >= MAX_VALUE）。
        int strictestRequired = 0;
        int strictestRank = Integer.MIN_VALUE;
        boolean anyMatch = false;
        for (RecipeHolder<?> holder : all) {
            anyMatch = true;
            int required = TierHelper.requiredTierOf(holder.id());
            int rank = TierHelper.selectionRank(required);
            if (rank > strictestRank) {
                strictestRank = rank;
                strictestRequired = required;
            }
        }
        if (anyMatch && !TierHelper.isTierAllowed(TierHelper.getAdmissionTier(machine), strictestRequired)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 把 {@code process()} 里那次 {@code AllRecipeTypes.find(...)} 的结果替换为
     * 「够得着的最高门槛」那条。原调用返回的实际类型是
     * {@code Optional<RecipeHolder<MillingRecipe>>}，泛型擦除后即
     * {@code Optional<RecipeHolder>}，因此这里按裸类型接收再原样返回同类型即可。
     */
    @ModifyExpressionValue(
            method = "process",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/AllRecipeTypes;find(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<?>> moreFormula$pickBestMilling(Optional<RecipeHolder<?>> original) {
        BlockEntity machine = (BlockEntity) (Object) this;
        Level level = machine.getLevel();
        if (level == null) {
            return original;
        }
        List<RecipeHolder<com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe>> all =
                TierHelper.millingRecipes(level, new RecipeWrapper(inputInv));
        if (all.isEmpty()) {
            return original;
        }
        Optional<RecipeHolder<com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe>> best =
                TierHelper.pickBestAllowed(machine, all);
        return best.map(h -> (RecipeHolder<?>) h);
    }
}
