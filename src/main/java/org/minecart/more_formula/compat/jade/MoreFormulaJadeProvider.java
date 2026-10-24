package org.minecart.more_formula.compat.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.minecart.more_formula.More_formula;
import org.minecart.more_formula.util.GoggleLines;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

/**
 * Jade 方块组件提供器：对受支持机器追加「机械等级 / 运行等级」两行。
 *
 * <p>两行内容来自 {@link GoggleLines#buildLines} 共享出口（与工程师护目镜逐字一致，
 * 含同一语言键与颜色）；不受支持的机器返回 {@code null}，这里什么都不追加。
 * 运行等级经既有的 Data Attachment 区块同步到达客户端，本提供器纯客户端读取。
 */
public class MoreFormulaJadeProvider implements IBlockComponentProvider {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(More_formula.MODID, "tier_lines");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockEntity be = accessor.getBlockEntity();
        List<Component> lines = GoggleLines.buildLines(be);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        tooltip.addAll(lines);
    }
}
