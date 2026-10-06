package org.minecart.more_formula.compat.handmade;

import net.minecraft.world.item.crafting.RecipeHolder;
import org.minecart.more_formula.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime gate shared by the optional Create: Hand Made mixins.
 * Hand Made must only consume recipes that are explicitly at Tier.ZERO
 * (an absent tier entry is also Tier.ZERO in More Formula's model).
 */
public final class HandMadeRecipeGate {
    private HandMadeRecipeGate() {
    }

    public static boolean isTierZero(RecipeHolder<?> holder) {
        return holder != null && Config.getRequiredTier(holder.id()) == 0;
    }

    public static List<RecipeHolder<?>> onlyTierZero(List<?> recipes) {
        if (recipes == null) {
            // 空值面按「拒绝」处理：无输入即无可放行配方，与 isTierZero(null)==false 同语义。
            return List.of();
        }
        List<RecipeHolder<?>> filtered = new ArrayList<>();
        for (Object recipe : recipes) {
            if (recipe instanceof RecipeHolder<?> holder && isTierZero(holder)) {
                filtered.add(holder);
            }
        }
        return filtered;
    }

    public static boolean isTierZeroResult(Object result) {
        return result instanceof RecipeHolder<?> holder && isTierZero(holder);
    }
}
