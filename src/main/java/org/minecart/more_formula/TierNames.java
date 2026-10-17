package org.minecart.more_formula;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 等级（{@code -1, 0..4}）到显示名的映射。
 *
 * <p>复用本模组既有的 lang 键（{@code more_formula.tier.N}，JEI 分级分类标题用的也是它），
 * 所以动作栏、护目镜、JEI 三处措辞天然一致。
 */
public final class TierNames {

    private TierNames() {
    }

    /**
     * 等级显示名。
     *
     * <p>0 级没有门槛，用「无」（而不是「0 级」）更贴近玩家直觉：
     * 它表示这台机器只做无门槛配方。
     */
    public static MutableComponent display(int tier) {
        if (tier == 0) {
            return Component.translatable("more_formula.tier.0");
        }
        return Component.translatable("more_formula.tier." + tier);
    }

    /** 带颜色的版本，用于动作栏/护目镜，让等级一眼可辨。 */
    public static MutableComponent colored(int tier) {
        return display(tier).withStyle(color(tier));
    }

    /** 各级配色：与原版材料观感一致（黄铜=金、下界=暗红、末影=紫、超越=青、创造=亮紫）。 */
    public static ChatFormatting color(int tier) {
        return switch (tier) {
            case -1 -> ChatFormatting.LIGHT_PURPLE;
            case 0 -> ChatFormatting.GRAY;
            case 1 -> ChatFormatting.GOLD;
            case 2 -> ChatFormatting.DARK_RED;
            case 3 -> ChatFormatting.DARK_PURPLE;
            case 4 -> ChatFormatting.AQUA;
            default -> ChatFormatting.GRAY;
        };
    }
}
