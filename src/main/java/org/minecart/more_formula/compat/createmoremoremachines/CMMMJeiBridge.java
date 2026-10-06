package org.minecart.more_formula.compat.createmoremoremachines;

import net.tomorrow325.createmoremoremachines.common.registry.CMMMAdvancedMachineTypes;
import net.yxiao233.createmoremachines.api.registry.BuiltInAdvancedMachineTypes;

/**
 * CreateMoreMoreMachines（可选依赖）注册表级机器条目 → JEI 的桥接。
 *
 * <p>本类对 CMMM 类型的直接引用是刻意的，但**只允许**两种调用方：
 * <ol>
 *   <li>{@code MoreFormulaJeiPlugin} —— 仅当 {@code FMLLoader.getLoadingModList()}
 *       确认 CMMM 在场后才经 {@code Class.forName} 反射加载本类；</li>
 *   <li>（无）—— 与 {@link CMMMTierBridge} 不同，本类没有 mixin 调用方。</li>
 * </ol>
 * 其他任何路径在未安装 CMMM 时加载本类都会 NoClassDefFoundError，因此不要扩大调用面。
 *
 * <p>为什么方法签名返回 CMM API 的 {@code AdvancedMachineType<?>} 而不返回 CMMM 的具体字段类型：
 * 调用方（JEI 插件）已经 import 了 CMM 的 API（必需依赖），零 CMMM 类型外泄可以让编译期
 * 依赖检查一目了然；字段本身的泛型擦除后就是 {@code AdvancedMachineType}，无运行期成本。
 */
public final class CMMMJeiBridge {

    private CMMMJeiBridge() {
    }

    /**
     * CMMM 的分级破碎轮注册表条目（brass..creative 五档，
     * {@code getAdvancedMechanicals()} 的键为 {@code createmoremachines:<tier>}，
     * 与 CMM 本体机器共享 {@code CMMTier} 实例）。
     */
    public static BuiltInAdvancedMachineTypes.AdvancedMachineType<?> crushingWheel() {
        return CMMMAdvancedMachineTypes.CRUSHING_WHEEL;
    }

    /**
     * CMMM 的分级机械锯注册表条目，键约定同上。CMM 2.7 本体没有分级锯
     * （对 SAW 调用了 {@code withoutAll()}），本条目只在 CMMM 在场时存在。
     */
    public static BuiltInAdvancedMachineTypes.AdvancedMachineType<?> mechanicalSaw() {
        return CMMMAdvancedMachineTypes.MECHANICAL_SAW;
    }
}
