package org.minecart.more_formula.compat.jei.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Copy of Create's {@link com.simibubi.create.compat.jei.category.animations.AnimatedCrushingWheels}
 * that renders a tier-specific crushing wheel instead of the vanilla one.
 *
 * <p>为什么仿 {@link TieredAnimatedPress} 模式而不复用原版动画：原版破碎轮/磨石是
 * 0 级机器、会被世界内 mixin 拦截门槛配方，再拿它们当分级分类的视觉就是误导。
 * CMMM 没有分级磨石方块，milling 配方实际由分级破碎轮控制器执行，
 * 因此 milling 分类渲染单个分级破碎轮（{@code single=true}）。
 */
public class TieredAnimatedCrushingWheels extends AnimatedKinetics {

    private final BlockState wheel;
    private final boolean single;

    public TieredAnimatedCrushingWheels(BlockState wheel, boolean single) {
        this.wheel = wheel.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        this.single = single;
    }

    @Override
    public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(xOffset, yOffset, 100);
        matrixStack.mulPose(Axis.YP.rotationDegrees(-22.5f));
        int scale = 22;

        blockElement(wheel)
                .rotateBlock(0, 90, -getCurrentAngle())
                .scale(scale)
                .render(graphics);

        if (!single) {
            blockElement(wheel)
                    .rotateBlock(0, 90, getCurrentAngle())
                    .atLocal(2, 0, 0)
                    .scale(scale)
                    .render(graphics);
        }

        matrixStack.popPose();
    }
}
