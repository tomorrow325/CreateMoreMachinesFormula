package org.minecart.more_formula.compat.jei.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copy of Create's {@link com.simibubi.create.compat.jei.category.animations.AnimatedSaw}
 * that renders a tier-specific saw body instead of the vanilla one.
 *
 * <p>刀片 partial 仍用 Create 命名空间（CMMM 不提供任何 partial 模型），
 * 分级语义由锯体方块状态承载。
 */
public class TieredAnimatedSaw extends AnimatedKinetics {

    private final BlockState body;

    public TieredAnimatedSaw(BlockState body) {
        this.body = body.setValue(SawBlock.FACING, Direction.UP);
    }

    @Override
    public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(xOffset, yOffset, 0);
        matrixStack.translate(0, 0, 200);
        matrixStack.translate(2, 22, 0);
        matrixStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
        matrixStack.mulPose(Axis.YP.rotationDegrees(22.5f + 90));
        int scale = 25;

        blockElement(shaft(Direction.Axis.X))
                .rotateBlock(-getCurrentAngle(), 0, 0)
                .scale(scale)
                .render(graphics);

        blockElement(body)
                .rotateBlock(0, 0, 0)
                .scale(scale)
                .render(graphics);

        blockElement(AllPartialModels.SAW_BLADE_VERTICAL_ACTIVE)
                .rotateBlock(0, -90, -90)
                .scale(scale)
                .render(graphics);

        matrixStack.popPose();
    }
}
