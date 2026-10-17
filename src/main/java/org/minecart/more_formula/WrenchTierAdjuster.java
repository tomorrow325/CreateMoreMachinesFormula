package org.minecart.more_formula;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.minecart.more_formula.util.CrushingWheelPair;
import org.minecart.more_formula.util.MachineRoster;
import org.minecart.more_formula.util.TierHelper;

/**
 * 用 Create 扳手<b>普通右键</b>循环调整机器的「运行等级」。
 *
 * <h2>为什么用事件而不是给方块加 {@code IWrenchable}</h2>
 * CMM / CMMM 的机器方块既<b>没有</b>实现 {@code IWrenchable}，也不在
 * {@code create:wrench_pickup} 标签里（两条都已用字节码/jar 内容确认），
 * 所以扳手普通右键在它们身上原本<b>什么都不做</b>（走 {@code Item.useOn} 返回 PASS）。
 * 因此这里监听右键事件即可，既不用给 CMM 的方块打 mixin，也不抢任何既有交互。
 *
 * <h2>触发条件（严格）</h2>
 * <ul>
 *   <li>手持 Create 扳手（{@code create:wrench} 物品）。</li>
 *   <li>目标方块实体属于受支持机器的其中之一（名单见 {@link MachineRoster}）。</li>
 *   <li>非潜行 —— 潜行留给将来的「拆机」等语义，避免误触。</li>
 *   <li>真实等级 &gt; 0 或为创造级（0 级机器没有可调空间，按下去不会有任何反馈）。</li>
 * </ul>
 *
 * <h2>破碎轮特例</h2>
 * 玩家点的是「轮子」，但门槛判定发生在配对的「控制器」上，因此这里
 * 把运行等级写到控制器（见 {@link CrushingWheelPair}）。
 *
 * <h2>只改「配方优先级」，不动速度与并行</h2>
 * 速度与并行倍率读的是 CMM/CMMM 自己的 {@code getTier()}（出厂等级，不可变），
 * 本功能把运行等级存在<b>另外一份</b>数据里，因此调它不会改变处理速度与并行数。
 */
@EventBusSubscriber(modid = More_formula.MODID)
public final class WrenchTierAdjuster {

    private WrenchTierAdjuster() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || !player.mayBuild()) {
            return;
        }
        // 潜行不处理：留作将来的其它语义（例如拆机），避免误触。
        if (player.isShiftKeyDown()) {
            return;
        }
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!isCreateWrench(held)) {
            return;
        }
        BlockEntity clicked = level.getBlockEntity(event.getPos());
        if (clicked == null) {
            return;
        }

        // 破碎轮：把目标换成配对的控制器（真正持有等级、跑配方的那半）。
        BlockEntity target = resolveTarget(level, clicked, event.getPos());
        if (target == null) {
            return;
        }
        if (!MachineRoster.isSupported(target)) {
            return;
        }
        // 先问「这台机器有没有可调空间」，再算下一档。
        // 不能只靠 `next == current` 兜底：0 级机器的 cycle 返回 0，而「未设置」是
        // Integer.MIN_VALUE，两者不相等 —— 那样会白写一次 0 并弹出「已调整为 无」，
        // 一个按下去什么都没改变的假提示。
        if (!CrushingWheelPair.isAdjustable(target)) {
            return;
        }

        int realTier = TierHelper.getRealMachineTier(target);
        int current = TierHelper.getRunningTier(target);
        int next = MachinePriority.cycle(current, realTier);
        if (next == current) {
            // 兜底：循环回不到别处时也不消耗、不提示。
            return;
        }

        MachinePriority.set(target, next);
        pushToClients(target);

        // 事件层面「吃掉」这次右键：阻止后续的方块/物品交互。
        event.setCanceled(true);

        // 动作栏提示 + 音效。
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(message(next), true);
        }
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, event.getPos());

        More_formula.LOGGER.debug("more_formula: {} running tier -> {} (real {})",
                target.getClass().getSimpleName(), next, realTier);
    }

    /**
     * 扳手判定：用 {@code AllItems.WRENCH} 的条目比较，而不是硬编码物品 id ——
     * 这样 Create 换 id 或包一层变体时仍然命中。
     */
    private static boolean isCreateWrench(ItemStack stack) {
        return !stack.isEmpty() && stack.is(AllItems.WRENCH.get());
    }

    /**
     * 把「玩家点的方块」解析成「应该写数据的方块实体」。
     *
     * <p>破碎轮：点轮子 → 找配对控制器；找不到控制器（例如只有半个结构）时
     * 回退到轮子本身，避免玩家按了毫无反应。
     */
    private static BlockEntity resolveTarget(Level level, BlockEntity clicked, net.minecraft.core.BlockPos pos) {
        if (!CrushingWheelPair.isWheel(clicked)) {
            return clicked;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity controller = CrushingWheelPair.findController(level, pos, state);
        return controller != null ? controller : clicked;
    }

    /**
     * 是否为「运行等级可调」的机器。
     *
     * <p>名单只有一份，在 {@link MachineRoster} —— 护目镜显示问的是同一个来源，
     * 避免「能调但不显示」这类只会在游戏里暴露的偏差。
     */
    private static boolean isSupportedMachine(BlockEntity be) {
        return MachineRoster.isSupported(be);
    }

    /**
     * 立即把新值同步给正在跟踪该区块的玩家（护目镜要读它）。
     *
     * <p>{@code syncData} 是 Attachment 的显式同步入口；区块首次下发时
     * {@code AttachmentSync.onChunkSent} 也会带上，所以两条路径都覆盖。
     */
    private static void pushToClients(BlockEntity be) {
        be.syncData(MachinePriority.RUNNING_TIER.get());
    }

    /**
     * 动作栏文案：「配方优先已被调整为：XX」。
     *
     * <p>用「配方优先」而不是「运行等级」，因为玩家按扳手时关心的是
     * 「这台机器接下来按哪一档挑配方」；「运行等级」是护目镜里的技术措辞。
     *
     * <p>底色统一灰色，等级名用 {@link TierNames#colored} 自带颜色 ——
     * 子段的显式颜色优先于父段继承色，所以等级名不会被灰色吃掉。
     */
    private static Component message(int tier) {
        MutableComponent value = TierNames.colored(tier);
        MutableComponent text = Component.translatable("more_formula.running_tier.adjusted", value);
        return text.withStyle(ChatFormatting.GRAY);
    }
}
