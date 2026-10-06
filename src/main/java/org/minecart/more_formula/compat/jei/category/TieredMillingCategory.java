package org.minecart.more_formula.compat.jei.category;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory.Info;
import com.simibubi.create.compat.jei.category.MillingCategory;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;
import org.minecart.more_formula.compat.jei.animation.TieredAnimatedCrushingWheels;

public class TieredMillingCategory extends MillingCategory {
    private final TieredAnimatedCrushingWheels crushingWheels;

    public TieredMillingCategory(Info<AbstractCrushingRecipe> info, BlockState wheelState) {
        super(info);
        // CMMM 没有分级磨石方块，milling 配方实际由分级破碎轮控制器执行
        // （控制器 findRecipe 先查 CRUSHING 再回退 MILLING），动画与实际执行者一致。
        this.crushingWheels = new TieredAnimatedCrushingWheels(wheelState, true);
    }

    @Override
    public void draw(AbstractCrushingRecipe recipe, IRecipeSlotsView iRecipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_ARROW.render(graphics, 85, 32);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 43, 4);
        this.crushingWheels.draw(graphics, 48, 27);
    }
}
