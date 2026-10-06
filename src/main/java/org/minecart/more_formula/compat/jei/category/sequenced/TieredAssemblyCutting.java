package org.minecart.more_formula.compat.jei.category.sequenced;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.minecraft.client.gui.GuiGraphics;
import org.minecart.more_formula.compat.jei.animation.TieredAnimatedSaw;

/**
 * 分级序列装配里的「锯切」工序。
 *
 * <p>CMM 2.7 本体没有发布任何分级锯（{@code CMMTierPlugin} 对 SAW 调用了
 * {@code withoutAll()}），但可选依赖 CMMM 提供了五档分级锯。因此这里是条件渲染：
 * {@code context.sawState()} 非 null（CMMM 在场且该档注册了分级锯）时按原版
 * {@code AssemblyCutting} 的几何渲染分级锯，否则委托 Create 原版的锯动画 ——
 * 与 CMMM 缺席时的现状完全一致。
 */
public class TieredAssemblyCutting extends TieredSequencedAssemblySubCategory {

    private final TieredAnimatedSaw saw;
    private final SequencedAssemblySubCategory vanilla;

    public TieredAssemblyCutting(TieredMachineContext context) {
        super(25);
        this.saw = context.sawState() != null ? new TieredAnimatedSaw(context.sawState()) : null;
        this.vanilla = context.sawState() != null ? null : new SequencedAssemblySubCategory.AssemblyCutting();
    }

    @Override
    public void draw(SequencedRecipe<?> recipe, GuiGraphics graphics, double mouseX, double mouseY, int index) {
        if (this.saw != null) {
            PoseStack ms = graphics.pose();
            ms.pushPose();
            ms.translate(0, 51.5f, 0);
            ms.scale(.6f, .6f, .6f);
            this.saw.draw(graphics, getWidth() / 2, 30);
            ms.popPose();
            return;
        }
        this.vanilla.draw(recipe, graphics, mouseX, mouseY, index);
    }
}
