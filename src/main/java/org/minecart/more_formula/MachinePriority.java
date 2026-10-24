package org.minecart.more_formula;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 「运行等级」（玩家手动设定的配方等级优先级）的存储。
 *
 * <h2>为什么是 Data Attachment，而不是直接改机器等级</h2>
 * CMM 的等级字段是 {@code private final CMMTier tier}，构造时定死并写进 NBT，
 * <b>改不了</b>。所以运行等级必须是<b>另一个独立的值</b>。
 * 这同时天然满足「调低后处理速度与并行不变」：那两项倍率走 CMM 自己的
 * {@code CMMTier}（真实等级），与这里无关。
 *
 * <h2>为什么选 Data Attachment</h2>
 * {@code BlockEntity extends AttachmentHolder}，而
 * {@code BlockEntity.loadAdditional/saveAdditional} 内部会自动调用
 * {@code deserializeAttachments/serializeAttachments}（已用字节码确认），
 * 所以<b>不需要给 CMM 的任何类打 mixin</b>，NBT 持久化是白送的。
 * 同步也一样：{@code sync(...)} 之后，方块实体随区块正常同步策略下发给客户端，
 * 护目镜 overlay 每帧读取不产生额外流量。
 *
 * <h2>取值语义</h2>
 * <ul>
 *   <li>{@code Integer.MIN_VALUE}（{@link #UNSET}）= <b>未设置</b>，跟随机器真实等级。
 *       用哨兵值而不是「等于真实等级」是为了能区分「玩家手动调成了和真实等级相同的值」
 *       与「从没调过」—— 前者在护目镜里应显示为手动值。</li>
 *   <li>{@code 0..4} = 手动指定的运行等级。</li>
 *   <li>{@code -1} = 创造档（只有创造级机器循环得到，见 {@link #cycle}）。</li>
 * </ul>
 * 未设置是默认值，因此<b>现有存档加载后行为完全不变</b>。
 */
public final class MachinePriority {

    /** 未设置的哨兵值。任何真实等级都取不到这个数，故可安全区分「没调过」。 */
    public static final int UNSET = PriorityRules.UNSET;

    /** 运行等级的最低普通档（0 = 只做无门槛配方）。 */
    public static final int MIN_CYCLE_TIER = PriorityRules.MIN_CYCLE_TIER;
    /** 运行等级的最高普通档（4 = 超越）。创造档 -1 单独处理。 */
    public static final int MAX_CYCLE_TIER = PriorityRules.MAX_CYCLE_TIER;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, More_formula.MODID);

    /**
     * 运行等级附件。
     *
     * <p>用 {@code Codec.INT} 序列化 + {@code ByteBufCodecs.VAR_INT} 同步：
     * 一个 int 就够了，没必要自定义 NBT 结构。
     * 默认值取 {@link #UNSET}，所以没调过的机器不占任何存档空间
     * （Attachment 是懒创建的，只有 {@code setData} 过的才会被序列化）。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> RUNNING_TIER =
            ATTACHMENT_TYPES.register("running_tier", () -> AttachmentType
                    .builder(() -> UNSET)
                    .serialize(new IAttachmentSerializer<Tag, Integer>() {
                        @Override
                        public Integer read(IAttachmentHolder holder, Tag tag, HolderLookup.Provider provider) {
                            // 兼容旧数据：不是数字就当作未设置。
                            return tag instanceof net.minecraft.nbt.NumericTag num ? num.getAsInt() : UNSET;
                        }

                        @Override
                        public Tag write(Integer value, HolderLookup.Provider provider) {
                            return net.minecraft.nbt.IntTag.valueOf(value);
                        }
                    })
                    .sync(new net.neoforged.neoforge.attachment.AttachmentSyncHandler<Integer>() {
                        @Override
                        public void write(RegistryFriendlyByteBuf buf, Integer value, boolean initialSync) {
                            buf.writeVarInt(value);
                        }

                        @Override
                        public Integer read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, Integer previous) {
                            return buf.readVarInt();
                        }
                    })
                    .build());

    private MachinePriority() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    /**
     * 读取机器的手动运行等级；未设置时返回 {@link #UNSET}。
     *
     * <p>用 {@code getExistingDataOrNull} 而不是 {@code getData}：后者会「读即创建」，
     * 把每台被看一眼的机器都变成有附件数据，白白撑大存档。
     */
    public static int getRaw(net.minecraft.world.level.block.entity.BlockEntity be) {
        Integer v = be.getExistingDataOrNull(RUNNING_TIER);
        return v == null ? UNSET : v;
    }

    public static boolean isSet(net.minecraft.world.level.block.entity.BlockEntity be) {
        return getRaw(be) != UNSET;
    }

    public static void set(net.minecraft.world.level.block.entity.BlockEntity be, int tier) {
        be.setData(RUNNING_TIER, tier);
    }

    public static void clear(net.minecraft.world.level.block.entity.BlockEntity be) {
        be.removeData(RUNNING_TIER);
    }

    /**
     * 循环取值。规则本体在 {@link PriorityRules#cycle}（纯函数、可离线断言），
     * 这里只做转发 —— 避免两处实现漂移。
     *
     * @param current 当前运行等级；传 {@link #UNSET} 表示未设置（从真实等级开始算）
     * @param realTier 机器真实等级（-1 表示创造级）
     * @return 下一个运行等级；若该机器不可调则返回 {@code realTier}
     */
    public static int cycle(int current, int realTier) {
        return PriorityRules.cycle(current, realTier);
    }
}
