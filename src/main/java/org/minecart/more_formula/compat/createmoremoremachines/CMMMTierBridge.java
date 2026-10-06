package org.minecart.more_formula.compat.createmoremoremachines;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.tomorrow325.createmoremoremachines.api.content.crushing_wheel.CMMCrushingWheelBlockEntity;
import net.tomorrow325.createmoremoremachines.api.content.crushing_wheel.CMMCrushingWheelControllerBlockEntity;
import net.tomorrow325.createmoremoremachines.api.content.mechanical_saw.CMMMechanicalSawBlockEntity;
import org.minecart.more_formula.util.TierHelper;

/**
 * CreateMoreMoreMachines（可选依赖）方块实体 → 本模组等级的桥接。
 *
 * <p>本类对 CMMM 类型的直接引用是刻意的，但**只允许**两种调用方：
 * <ol>
 *   <li>{@code more_formula-createmoremoremachines.mixins.json} 里的 mixin 处理器 ——
 *       该配置由 {@link MoreFormulaCreateMoreMoreMachinesMixinConfigPlugin} 按模组在场与否门控；</li>
 *   <li>{@link TierHelper} 的反射调用 —— 只有当机器类名落在 CMMM 包名下（= 模组必然在场）
 *       时才会走到这里。</li>
 * </ol>
 * 其他任何路径在未安装 CMMM 时加载本类都会 NoClassDefFoundError，因此不要扩大调用面。
 */
public final class CMMMTierBridge {

    private CMMMTierBridge() {
    }

    /**
     * CMMM 机器 → 本模组等级。CMMM 复用 CMM 的 {@code CMMTier}
     * （brass=2 / netherite=3 / end=4 / beyond=5 / creative=-1），
     * 与 CMM 本体机器共用同一次平移。
     */
    public static int getMachineTier(BlockEntity machine) {
        if (machine instanceof CMMCrushingWheelControllerBlockEntity controller) {
            return TierHelper.toFormulaTier(controller.getTier().getTierValue());
        }
        if (machine instanceof CMMCrushingWheelBlockEntity wheel) {
            return TierHelper.toFormulaTier(wheel.getTier().getTierValue());
        }
        if (machine instanceof CMMMechanicalSawBlockEntity saw) {
            return TierHelper.toFormulaTier(saw.getTier().getTierValue());
        }
        return 0;
    }
}
