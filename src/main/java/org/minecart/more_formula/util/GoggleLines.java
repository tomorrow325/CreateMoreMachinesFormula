package org.minecart.more_formula.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.minecart.more_formula.MachinePriority;
import org.minecart.more_formula.TierNames;

import java.util.List;

/**
 * 护目镜里追加的两行：「机械等级」+「运行等级」。
 *
 * <p>抽成共享工具，原因有二：
 * <ol>
 *   <li>注入点分散在多个 mixin（{@code KineticBlockEntity} 一次覆盖大部分，
 *       {@code DeployerBlockEntity} 与 {@code CMMSpoutBlockEntity} 各自覆写了方法、
 *       需要单独注入），显示规则必须完全一致；</li>
 *   <li>破碎轮的显示要从「轮子」反查「控制器」的真实数据，这段逻辑不适合写在 mixin 里。</li>
 * </ol>
 *
 * <h2>显示规则（已与需求确认）</h2>
 * 恒显示两行：机械等级（真实）与运行等级（当前生效）。
 * 未手动设置时运行等级等于真实等级，两行相同 —— 这是有意为之：
 * 玩家一眼能看出「这台机器本来就这个级」还是「被调过」。
 */
public final class GoggleLines {

    private GoggleLines() {
    }

    /**
     * 向 tooltip 追加两行。不是受支持机器时什么都不做，因此可以安全地在
     * {@code KineticBlockEntity} 这种宽泛的祖先上调用 —— 原版 Create 机器
     * 会因为不在支持名单里而被跳过，行为不变。
     */
    public static void append(List<Component> tooltip, BlockEntity be) {
        List<Component> lines = buildLines(be);
        if (lines == null) {
            return;
        }
        tooltip.addAll(lines);
    }

    /**
     * 构造「机械等级 + 运行等级」两行；不是受支持机器（或数据源缺失）时返回 {@code null}。
     *
     * <p>护目镜 mixin 与 Jade 提供器共用本出口，保证两个显示面逐字一致。
     */
    @Nullable
    public static List<Component> buildLines(BlockEntity be) {
        if (be == null) {
            return null;
        }
        BlockEntity dataSource = resolveDataSource(be);
        if (dataSource == null) {
            return null;
        }
        int realTier = TierHelper.getRealMachineTier(dataSource);
        int running = TierHelper.getRunningTier(dataSource);
        int effective = running == MachinePriority.UNSET ? realTier : running;

        return List.of(
                Component.translatable("more_formula.goggle.machine_tier",
                                TierNames.colored(realTier))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("more_formula.goggle.running_tier",
                                TierNames.colored(effective))
                        .withStyle(ChatFormatting.GRAY));
    }

    /**
     * 找到「真正持有运行等级」的方块实体。
     *
     * <p>只有破碎轮需要转换：玩家看的是轮子，数据在配对的控制器上。
     * 其余机器返回自身。
     *
     * <p>也承担「是不是受支持机器」的判定：不受支持 → 返回 {@code null} → 不显示。
     * 名单来自 {@link MachineRoster}，与扳手用的是同一份。
     */
    private static BlockEntity resolveDataSource(BlockEntity be) {
        // 破碎轮轮子：转去配对的控制器。
        if (CrushingWheelPair.isWheel(be)) {
            // 先做「是不是分级轮子」这一步再去找邻居：原版 Create 的轮子也命中 isWheel，
            // 若先 findController 就会为它白扫一圈邻居（而它永远不显示这两行）。
            if (!CrushingWheelPair.isTieredWheel(be) || be.getLevel() == null) {
                return null;
            }
            // 找不到控制器时不显示（半个结构，没意义）。
            return CrushingWheelPair.findController(be.getLevel(), be.getBlockPos(), be.getBlockState());
        }

        return MachineRoster.isSupported(be) ? be : null;
    }
}
