package org.minecart.more_formula.mixin;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 压机（0 级原版机器）的门槛闸门，并负责「同输入多配方择优」。
 *
 * <p>Create 的 {@code getRecipe} 是「先序列装配、再 {@code AllRecipeTypes.find}」，
 * 而 {@code find} 只回 {@code byType().filter(matches).findFirst()} —— 同一输入写了
 * t1/t2 两条 pressing 时，先注册的 t1 永远先命中，等级够的机器也会执行低级配方。
 * 这里改成把「Create 原生选中的那条」与「输入下所有普通 pressing 配方」合成候选池，
 * 滤掉等级不够的，再取门槛最高的一条。
 *
 * <p>为什么要把原生结果并进候选池而不是直接替换：序列装配展开出的工序配方不在
 * {@code RecipeManager} 里注册（是 {@code SequencedAssemblyRecipe} 按需生成的），
 * {@code getRecipesFor} 查不到它。丢掉原生结果会让压机做不了任何装配线工序。
 * 它的 id 是<b>外层装配线</b>的 id，因此参与择优时用的是装配线的门槛，语义正确。
 */
@Mixin(MechanicalPressBlockEntity.class)
public abstract class MechanicalPressBlockEntityMixin {
    @Inject(method = "getRecipe", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gatePressing(ItemStack item, CallbackInfoReturnable<Optional<RecipeHolder<PressingRecipe>>> cir) {
        BlockEntity machine = (BlockEntity) (Object) this;
        Level level = machine.getLevel();
        if (level == null) {
            return;
        }
        Optional<RecipeHolder<PressingRecipe>> nativePick = cir.getReturnValue();

        List<RecipeHolder<PressingRecipe>> candidates = new ArrayList<>();
        // 原生结果优先入池：并列（同门槛）时保留 Create 的选择（含序列装配工序）。
        nativePick.ifPresent(candidates::add);
        candidates.addAll(TierHelper.matchingRecipes(
                level, AllRecipeTypes.PRESSING.getType(), new SingleRecipeInput(item)));

        if (candidates.isEmpty()) {
            return;
        }
        cir.setReturnValue(TierHelper.pickBestAllowed(machine, candidates));
    }
}
