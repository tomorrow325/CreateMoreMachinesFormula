package org.minecart.more_formula.mixin;

import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 工作盆类机器（搅拌器、压机压实、混合等）的门槛闸门 + 「同输入多配方择优」。
 *
 * <p>{@code getMatchingRecipes} 的返回值随后被 {@code updateBasin} 用
 * {@code list.get(0)} 取作 {@code currentRecipe}（已用字节码确认，
 * 见 {@code BasinOperatingBlockEntity.updateBasin} 的 {@code invokeinterface #97 get:(I)}），
 * 所以这里的<b>顺序即选择结果</b>。
 *
 * <p>旧实现只做「滤掉等级不够的」，于是同一输入写了 t1/t2 两条 mixing 时，
 * 列表里 t1 仍在 t2 前面（按注册顺序），{@code get(0)} 拿到的还是 t1 ——
 * 这正是「等级够的机器执行低级配方」的成因。现在改为
 * {@link TierHelper#sortMatchingByTier}：先滤、再按等级降序稳定排序。
 *
 * <p><b>子类覆写时的作用范围</b>（已逐个核对字节码）：本 mixin 打在
 * {@code BasinOperatingBlockEntity} 基类的方法上，因此只对该方法体内的候选生效。
 * <ul>
 *   <li>{@code MechanicalMixerBlockEntity} 覆写了 {@code getMatchingRecipes}，
 *       但第一件事就是 {@code invokespecial ...BasinOperatingBlockEntity.getMatchingRecipes()}
 *       —— 本注入照常生效；它随后把自己找到的酿造配方 {@code List.add} 到<b>末尾</b>，
 *       不会打乱我们已经排好的顺序。代价是：若某条酿造配方与已排序的搅拌配方
 *       恰好同输入且门槛更低，它会落在后面（即被我们排到前面的高门槛配方优先）——
 *       这正是期望行为。</li>
 *   <li>CMM 的分级搅拌器/压机（{@code CMMMechanicalMixerBlockEntity} /
 *       {@code CMMMechanicalPressBlockEntity}）都<b>没有</b>覆写该方法，直接走基类实现。</li>
 * </ul>
 */
@Mixin(BasinOperatingBlockEntity.class)
public abstract class BasinOperatingBlockEntityMixin {
    @Inject(method = "getMatchingRecipes", at = @At("RETURN"), cancellable = true)
    private void moreFormula$gateBasinRecipes(CallbackInfoReturnable<List<Recipe<?>>> cir) {
        List<Recipe<?>> recipes = cir.getReturnValue();
        if (recipes == null || recipes.isEmpty()) {
            return;
        }
        BlockEntity machine = (BlockEntity) (Object) this;
        cir.setReturnValue(TierHelper.sortMatchingByTier(machine.getLevel(), machine, recipes));
    }
}
