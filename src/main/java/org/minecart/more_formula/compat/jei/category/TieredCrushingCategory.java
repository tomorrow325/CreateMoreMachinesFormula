package org.minecart.more_formula.compat.jei.category;

import com.simibubi.create.compat.jei.category.CrushingCategory;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory.Info;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;
import org.minecart.more_formula.compat.jei.animation.TieredAnimatedCrushingWheels;

public class TieredCrushingCategory extends CrushingCategory {
    private final TieredAnimatedCrushingWheels crushingWheels;

    public TieredCrushingCategory(Info<AbstractCrushingRecipe> info, BlockState wheelState) {
        super(info);
        this.crushingWheels = new TieredAnimatedCrushingWheels(wheelState, false);
    }

    @Override
    public void draw(AbstractCrushingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 72, 7);

        this.crushingWheels.draw(graphics, 62, 59);
    }
}
