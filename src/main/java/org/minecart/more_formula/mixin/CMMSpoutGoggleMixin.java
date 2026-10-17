package org.minecart.more_formula.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.yxiao233.createmoremachines.api.content.spout.CMMSpoutBlockEntity;
import org.minecart.more_formula.util.GoggleLines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * {@code CMMSpoutBlockEntity} 是 8 台里<b>唯一</b>不继承 {@code KineticBlockEntity} 的：
 * 它直接 extends {@code SmartBlockEntity} 并自己 implements
 * {@code IHaveGoggleInformation}、自己实现 {@code addToGoggleTooltip}
 * （已用字节码确认）。因此既不走 {@link KineticGoggleMixin}，也不走 Deployer 那条，
 * 必须单独注入。
 *
 * <p>注入点同为 {@code RETURN}，只追加我们的两行，保留注液器原有的流体等信息。
 */
@Mixin(CMMSpoutBlockEntity.class)
public abstract class CMMSpoutGoggleMixin {

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"))
    private void moreFormula$appendRunningTier(List<Component> tooltip, boolean isPlayerSneaking,
                                               CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        GoggleLines.append(tooltip, self);
    }
}
