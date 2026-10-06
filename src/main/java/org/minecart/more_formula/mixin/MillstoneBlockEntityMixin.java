package org.minecart.more_formula.mixin;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.minecart.more_formula.util.TierHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版磨石是 0 级机器：带门槛的 milling 配方不允许进入磨石
 * （与原版压机/喷口拒绝门槛配方的语义一致）。
 *
 * <p>{@code canProcess} 是磨石唯一的插入闸门（{@code isItemValid} 调用），
 * 返回 false 时漏斗/传送带都无法把物品塞进来，物品留在来源处 —— 无损。
 * 按喷口同款「宁严勿漏」原则：同一输入存在多条匹配配方时，只要有一条带门槛
 * 就拒绝插入（磨石实际执行哪条取决于内部缓存，插入方无法预知）。
 *
 * <p>CMMM 的分级破碎轮对 milling 的回退查找走它自己的 {@code findRecipe}，
 * 由 CMMM 门控 mixin 拦截，与本 mixin 互不重叠。
 */
@Mixin(MillstoneBlockEntity.class)
public abstract class MillstoneBlockEntityMixin {
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
        for (RecipeHolder<Recipe<RecipeInput>> holder : level.getRecipeManager()
                .getAllRecipesFor(AllRecipeTypes.MILLING.getType())) {
            if (holder.value().matches(input, level) && !TierHelper.isAllowed(machine, holder.id())) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
