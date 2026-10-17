package org.minecart.more_formula.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.minecart.more_formula.util.GoggleLines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 给「继承 {@code KineticBlockEntity} 的那批受支持机器」的护目镜信息追加两行。
 *
 * <h2>为什么打在 {@code KineticBlockEntity} 上</h2>
 * CMM/CMMM 的机器方块实体本身<b>都不定义</b> {@code addToGoggleTooltip}（已逐个核对）：
 * 该方法由 Create 的 {@code KineticBlockEntity} 声明并实现，
 * 8 台机器里的 6 台直接继承它、{@code DeployerBlockEntity} 覆写（另有专门 mixin）、
 * 破碎轮的「轮子」也继承它。
 *
 * <p>打在这个共同祖先上，一次注入即可覆盖压机 / 搅拌器 / 动力锯 / 分级锯 /
 * 鼓风机 / 破碎轮轮子等，且<b>不必逐个类写 mixin</b>。
 *
 * <p>注入点选 {@code RETURN} 且用 {@code @Inject} 往列表里追加 ——
 * 不动 Create 自己的内容，只加我们的两行，因此机器原有的转速/流体等信息照常显示。
 */
@Mixin(KineticBlockEntity.class)
public abstract class KineticGoggleMixin {

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"))
    private void moreFormula$appendRunningTier(List<Component> tooltip, boolean isPlayerSneaking,
                                               CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        GoggleLines.append(tooltip, self);
    }
}
