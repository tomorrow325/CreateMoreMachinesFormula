package org.minecart.more_formula.util;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 「运行等级」可调机器的<b>唯一名单</b>。
 *
 * <h2>为什么要有这个类</h2>
 * 名单同时被两处需要：扳手右键（{@code WrenchTierAdjuster}）与护目镜显示
 * （{@code GoggleLines}）。此前两处各抄了一份类名清单 —— 改动名单时必须同步改两处，
 * 漏一处就会出现「能调但不显示」或「显示但调不动」这类只在游戏里才看得见的偏差。
 * 现在两边都问这里，名单只有一份。
 *
 * <h2>为什么用类名字符串而不是 {@code instanceof}</h2>
 * CMMM 是<b>可选依赖</b>。本类在主 mixin 配置（不门控）里被引用，任何对 CMMM 类型的
 * 直接引用都会在未安装该模组时抛 {@code NoClassDefFoundError}。类名字符串比较
 * 在模组缺席时只是「永远不命中」，绝对安全（与 {@link TierHelper} 的桥接同一套约束）。
 *
 * <h2>入册条件（两条都要满足）</h2>
 * <ol>
 *   <li>机器确实跑<b>带门槛的配方</b> —— 本模组已经在它的选配方路径上装了闸门
 *       （压机/搅拌器经工作盆、锯、破碎轮、喷口、部署器）；</li>
 *   <li>因此「运行等级」能真正改变它的行为，而不是一个按下去没反应的装饰。</li>
 * </ol>
 *
 * <h2>刻意未入册的机器</h2>
 * <ul>
 *   <li><b>CMMM 鼓风机（{@code encased_fan}，1.1 新增）</b>：它的配方是 Create 的
 *       <i>鼓风处理</i>（{@code splashing} / {@code haunting} / {@code blasting} /
 *       {@code smoking}），而本模组<b>从未</b>给这些配方装过闸门 ——
 *       {@code TierHelper} 只处理 pressing / mixing / compacting / filling /
 *       deploying / cutting / crushing / milling。
 *       原因在 API 形状：{@code FanProcessingType.canProcess(ItemStack, Level)}
 *       只拿得到「物品 + 世界」，<b>拿不到是哪台鼓风机</b>，因此无法按机器等级判定；
 *       唯一的收口点是 {@code AirCurrent.tickAffectedHandlers}（那里同时知道风扇与
 *       处理类型），而那属于「给鼓风处理加门槛」这个独立功能，不是本轮的范围。
 *       在没有闸门之前把它登记进来，只会让扳手显示「已调整为黄铜」却什么都不改变 ——
 *       宁可暂时不给这个控制，也不给一个假的。鼓风机本身照常工作，等级显示也照旧。</li>
 *   <li>不跑分级配方的 CMM 方块：工作盆、置物台（depot）、流体罐、蒸汽引擎。</li>
 * </ul>
 */
public final class MachineRoster {

    // ---- CMM（Create: More Machines）----
    private static final String CMM_PRESS =
            "net.yxiao233.createmoremachines.api.content.mechanical.press.CMMMechanicalPressBlockEntity";
    private static final String CMM_MIXER =
            "net.yxiao233.createmoremachines.api.content.mechanical.mixer.CMMMechanicalMixerBlockEntity";
    private static final String CMM_DEPLOYER =
            "net.yxiao233.createmoremachines.api.content.mechanical.deployer.CMMDeployerBlockEntity";
    private static final String CMM_SPOUT =
            "net.yxiao233.createmoremachines.api.content.spout.CMMSpoutBlockEntity";
    private static final String CMM_SAW =
            "net.yxiao233.createmoremachines.api.content.saw.CMMSawBlockEntity";

    // ---- CMMM（Create: More More Machines，可选依赖）----
    /** 破碎轮控制器：跑分级 crushing/milling 的那半（玩家点的是轮子，由 {@link CrushingWheelPair} 转过来）。 */
    public static final String CMMM_CRUSHING_WHEEL_CONTROLLER =
            "net.tomorrow325.createmoremoremachines.api.content.crushing_wheel.CMMCrushingWheelControllerBlockEntity";
    private static final String CMMM_MECHANICAL_SAW =
            "net.tomorrow325.createmoremoremachines.api.content.mechanical_saw.CMMMechanicalSawBlockEntity";

    private static final Set<String> SUPPORTED = Set.of(
            CMM_PRESS,
            CMM_MIXER,
            CMM_DEPLOYER,
            CMM_SPOUT,
            CMM_SAW,
            CMMM_CRUSHING_WHEEL_CONTROLLER,
            CMMM_MECHANICAL_SAW);

    private MachineRoster() {
    }

    /** 该方块实体是否为「运行等级可调」的机器。 */
    public static boolean isSupported(@Nullable BlockEntity be) {
        return be != null && isSupported(be.getClass().getName());
    }

    /** 纯字符串判定（离线可断言，且不会触碰任何可选依赖的类）。 */
    public static boolean isSupported(@Nullable String className) {
        return className != null && SUPPORTED.contains(className);
    }

    /** 名单条目数（离线回归断言用，防止名单被误改）。 */
    public static int size() {
        return SUPPORTED.size();
    }
}
