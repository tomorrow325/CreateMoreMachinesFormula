package org.minecart.more_formula.mixin;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.yxiao233.createmoremachines.api.content.mechanical.press.CMMMechanicalPressBlockEntity;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CMM 分级压机的门槛闸门 + 「同输入多配方择优」。
 *
 * <p>CMM 的 {@code getRecipe} 与 Create 原版同构：先序列装配、再
 * {@code AllRecipeTypes.find}（只回注册顺序第一条）。因此同一输入写了 t1/t2 两条
 * pressing 时，等级够的 CMM 压机也会执行低级配方。修法与
 * {@link MechanicalPressBlockEntityMixin} 一致：原生结果 + 输入下全部普通配方合成
 * 候选池，滤掉等级不够的，取门槛最高者。
 */
@Mixin(CMMMechanicalPressBlockEntity.class)
public abstract class CMMMechanicalPressBlockEntityMixin {
    @Inject(method = "getRecipe", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gatePressing(ItemStack item, CallbackInfoReturnable<Optional<RecipeHolder<PressingRecipe>>> cir) {
        BlockEntity machine = (BlockEntity) (Object) this;
        Level level = machine.getLevel();
        if (level == null) {
            return;
        }
        Optional<RecipeHolder<PressingRecipe>> nativePick = cir.getReturnValue();

        List<RecipeHolder<PressingRecipe>> candidates = new ArrayList<>();
        nativePick.ifPresent(candidates::add);
        candidates.addAll(TierHelper.matchingRecipes(
                level, AllRecipeTypes.PRESSING.getType(), new SingleRecipeInput(item)));

        if (candidates.isEmpty()) {
            return;
        }
        cir.setReturnValue(TierHelper.pickBestAllowed(machine, candidates));
    }
}
