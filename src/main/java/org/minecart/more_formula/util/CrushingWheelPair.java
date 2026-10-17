package org.minecart.more_formula.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import org.minecart.more_formula.PriorityRules;

/**
 * 分级破碎轮的「轮子 ↔ 控制器」配对。
 *
 * <h2>为什么需要它</h2>
 * 破碎轮由两个方块组成：玩家放置与看到的是 {@code CrushingWheelBlock}（轮子），
 * 而真正跑配方、持有等级的是夹在中间那个不可见的
 * {@code CrushingWheelControllerBlock}（控制器）。玩家右键命中的必然是<b>轮子</b>，
 * 但门槛判定发生在<b>控制器</b>上。
 *
 * <p>因此约定（已与需求确认）：<b>点轮子，数据写到配对的控制器上</b>。
 * 护目镜则相反 —— 玩家看的是轮子，所以轮子的 tooltip 要去读配对控制器的运行等级。
 *
 * <h2>配对规则（复刻 Create 自己的 {@code updateControllers}，已用字节码逐条核对）</h2>
 * 轮子与控制器<b>沿一条直线相邻</b>排列（{@code 轮子A — 控制器 — 轮子B}），
 * 而这条直线的方向与轮子的{@code AXIS}（旋转轴）<b>垂直</b>。
 *
 * <p>判据来自 Create 的 {@code CrushingWheelControllerBlock.lambda$updateSpeed$1}
 * 与 CMMM 的 {@code CMMCrushingWheelBlock.updateControllers}，两处都是同一条：
 * <pre>
 * if (dir.getAxis() == state.getValue(AXIS)) return;   // 轴向的邻居不是控制器
 * BlockPos controllerPos = pos.relative(dir);          // 轮子 → 控制器：1 格
 * BlockPos otherWheelPos = pos.relative(dir, 2);       // 轮子 → 另一只轮子：2 格
 * </pre>
 * 也就是说，<b>控制器在轮子「轴向垂直」的那 4 个方向之一</b>，而不是轴向邻居。
 *
 * <h2>为什么不直接用 {@code VALID}</h2>
 * {@code VALID} 由 Create 自己维护，可能在成对结构尚未成形时为 {@code false}；
 * 这里只认「类名是控制器」这一件事，配合 1 格邻接即可，判定更宽松也更稳。
 */
public final class CrushingWheelPair {

    private CrushingWheelPair() {
    }

    /**
     * 找到给定轮子方块对应的控制器方块实体。
     *
     * <p>先只认「轴向垂直」的邻居（Create / CMMM 的规范布局，见类注释）；
     * 万一那里没有，再把「沿轴」的邻居当兜底扫一遍 —— 这样即使将来布局变化、
     * 或结构被玩家改得只剩一半，也不会退化成「按了没反应」。
     *
     * @param level 世界
     * @param wheelPos 轮子所在位置
     * @param wheelState 轮子方块状态（用来取旋转轴）
     * @return 配对的控制器 BE；找不到返回 {@code null}
     */
    @Nullable
    public static BlockEntity findController(Level level, BlockPos wheelPos, BlockState wheelState) {
        Direction.Axis axis = wheelState.hasProperty(BlockStateProperties.AXIS)
                ? wheelState.getValue(BlockStateProperties.AXIS)
                : null;
        BlockEntity fallback = null;
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(wheelPos.relative(dir));
            if (be == null || !isController(be)) {
                continue;
            }
            if (axis == null || dir.getAxis() != axis) {
                // 轴向垂直 = Create / CMMM 的规范位置，直接采用。
                return be;
            }
            // 沿轴的邻居：只在找不到规范位置时兜底，不覆盖已找到的规范位置。
            if (fallback == null) {
                fallback = be;
            }
        }
        return fallback;
    }

    /**
     * 是否为「分级破碎轮」的控制器。
     *
     * <p>只做类名判断，不直接引用 CMMM 的类型 —— 本类在未安装 CMMM 时也可能被加载，
     * 直接引用会 NoClassDefFoundError（与 {@link TierHelper} 的桥接同一套约束）。
     */
    public static boolean isController(BlockEntity be) {
        String n = be.getClass().getName();
        // CMMM 分级控制器：走 CMMM 桥接拿等级；原版控制器：0 级。
        return n.equals("net.tomorrow325.createmoremoremachines.api.content."
                + "crushing_wheel.CMMCrushingWheelControllerBlockEntity")
                || n.equals("com.simibubi.create.content.kinetics.crusher."
                + "CrushingWheelControllerBlockEntity");
    }

    /** 是否为破碎轮的「轮子」（玩家可见/可点的那半）。 */
    public static boolean isWheel(BlockEntity be) {
        String n = be.getClass().getName();
        return n.equals("net.tomorrow325.createmoremoremachines.api.content."
                + "crushing_wheel.CMMCrushingWheelBlockEntity")
                || n.equals("com.simibubi.create.content.kinetics.crusher."
                + "CrushingWheelBlockEntity");
    }

    /**
     * 该方块实体是否是「玩家可点、可调运行等级」的破碎轮轮子。
     */
    public static boolean isTieredWheel(BlockEntity be) {
        if (!isWheel(be)) {
            return false;
        }
        // 只有 CMMM 的分级轮子有等级；原版轮子是 0 级、不可调。
        return be.getClass().getName().startsWith("net.tomorrow325.createmoremoremachines.");
    }

    /** 该方块实体的真实等级是否可调（分级机器且等级 &gt; 0 或创造级）。 */
    public static boolean isAdjustable(BlockEntity be) {
        // 规则本体在 PriorityRules（纯函数、可离线断言），这里只转发以免两处漂移。
        return PriorityRules.isAdjustable(TierHelper.getRealMachineTier(be));
    }
}
