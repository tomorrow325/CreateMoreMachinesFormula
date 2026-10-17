package org.minecart.more_formula.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.minecart.more_formula.util.GoggleLines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * {@code DeployerBlockEntity} <b>自己覆写</b>了 {@code addToGoggleTooltip}
 * （已用字节码确认），因此不会走 {@link KineticGoggleMixin} 那条注入 ——
 * 需要在这里单独追加一次，否则 CMM 分级部署器的护目镜看不到运行等级。
 *
 * <p>注入点同为 {@code RETURN}，只追加、不改 Create 原有内容。
 */
@Mixin(DeployerBlockEntity.class)
public abstract class DeployerGoggleMixin {

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"))
    private void moreFormula$appendRunningTier(List<Component> tooltip, boolean isPlayerSneaking,
                                               CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        GoggleLines.append(tooltip, self);
    }
}
