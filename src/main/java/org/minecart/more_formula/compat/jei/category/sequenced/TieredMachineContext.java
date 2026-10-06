package org.minecart.more_formula.compat.jei.category.sequenced;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resolved tier machine visuals shared by the tiered sequenced-assembly sub categories.
 *
 * <p>{@code sawState} 为 CMMM（可选依赖）的分级锯状态，缺失（CMMM 不在场
 * 或该档未注册锯）时为 {@code null}，锯切工序回退原版渲染。
 */
public record TieredMachineContext(BlockState pressBody, BlockState spoutBody,
                                   PartialModel[] spoutPartials, BlockState deployerBody,
                                   BlockState depotState, BlockState basinState,
                                   BlockState sawState) {
}
