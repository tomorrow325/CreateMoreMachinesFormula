package org.minecart.more_formula.compat.jei.category;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory.Info;
import com.simibubi.create.compat.jei.category.SawingCategory;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;
import org.minecart.more_formula.compat.jei.animation.TieredAnimatedSaw;

public class TieredCuttingCategory extends SawingCategory {
    private final TieredAnimatedSaw saw;

    public TieredCuttingCategory(Info<CuttingRecipe> info, BlockState sawState) {
        super(info);
        this.saw = new TieredAnimatedSaw(sawState);
    }

    @Override
    public void draw(CuttingRecipe recipe, IRecipeSlotsView iRecipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 70, 6);
        AllGuiTextures.JEI_SHADOW.render(graphics, 72 - 17, 42 + 13);

        this.saw.draw(graphics, 72, 42);
    }
}
