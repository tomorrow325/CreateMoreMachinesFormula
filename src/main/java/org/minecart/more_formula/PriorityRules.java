package org.minecart.more_formula;

/**
 * 「运行等级」的<b>纯规则</b>（不含任何 Minecraft / NeoForge 类型）。
 *
 * <h2>为什么要单独一个类</h2>
 * {@link MachinePriority} 持有 {@code DeferredRegister<AttachmentType<?>>}，
 * 它的静态初始化会触碰 NeoForge 的注册表（{@code NeoForgeRegistries.ATTACHMENT_TYPES}），
 * 因此<b>无法脱离游戏进程加载</b> —— 一旦在离线回归测试里引用它，
 * 就会 {@code ExceptionInInitializerError}。
 *
 * <p>循环与权重这两条规则恰恰是本次功能里最需要断言的部分（"只能调低不能调高"
 * "循环到底回到顶" "创造档的例外"），所以把它们下沉到本类：
 * 零依赖、可离线断言。
 */
public final class PriorityRules {

    /** 未设置的哨兵值。任何真实等级都取不到这个数，故可安全区分「没调过」。 */
    public static final int UNSET = Integer.MIN_VALUE;

    /** 运行等级的最低普通档（0 = 只做无门槛配方）。 */
    public static final int MIN_CYCLE_TIER = 0;
    /** 运行等级的最高普通档（4 = 超越）。创造档 -1 单独处理。 */
    public static final int MAX_CYCLE_TIER = 4;

    /** 创造级常量。与 {@link Config#CREATIVE_TIER} 同值，此处独立定义以免依赖 Config 的静态初始化顺序。 */
    public static final int CREATIVE_TIER = -1;

    private PriorityRules() {
    }

    /**
     * 循环取值（纯函数）：按「可调范围」从当前值推进到下一个值。
     *
     * <p>规则（已与需求逐条确认）：
     * <ul>
     *   <li>可调范围是 {@code 0..realTier}，因此<b>只能调低、不能调高</b>。</li>
     *   <li>循环到底后回到顶。</li>
     *   <li>{@code realTier <= 0} 的机器（原版机器或 0 级）无可调空间，返回 {@code realTier}。</li>
     *   <li><b>创造级机器例外</b>：{@code realTier == -1} 时顺序为
     *       {@code 4 → 3 → 2 → 1 → 0 → -1 → 4}（创造档接在 0 之后）。</li>
     * </ul>
     *
     * @param current 当前运行等级；{@link #UNSET} 表示未设置（从真实等级起算）
     * @param realTier 机器真实等级（-1 表示创造级）
     * @return 下一个运行等级；该机器不可调时返回 {@code realTier}
     */
    public static int cycle(int current, int realTier) {
        boolean creative = realTier == CREATIVE_TIER;
        if (!creative && realTier <= MIN_CYCLE_TIER) {
            // 原版机器（0 级）没有可调空间：0 已是最低档，且规则禁止调高。
            return realTier;
        }
        int from = (current == UNSET) ? realTier : current;

        if (creative) {
            // 创造机器：4 → 3 → 2 → 1 → 0 → -1(创造) → 4
            return switch (from) {
                case 4 -> 3;
                case 3 -> 2;
                case 2 -> 1;
                case 1 -> 0;
                case 0 -> CREATIVE_TIER;
                case CREATIVE_TIER -> MAX_CYCLE_TIER;
                default -> MAX_CYCLE_TIER;
            };
        }
        // 普通分级机器：realTier → realTier-1 → … → 0 → realTier
        if (from <= MIN_CYCLE_TIER) {
            return realTier;
        }
        return from - 1;
    }

    /**
     * 该<b>真实等级</b>是否留有可调空间。
     *
     * <p>只有分级机器（{@code 1..4}）与创造级（{@code -1}）能调；
     * {@code 0} 级（原版机器 / 没有任何等级信息的方块实体）没有可调空间。
     *
     * <p>为什么这是一条独立规则、需要单独断言：{@code cycle(UNSET, 0)} 返回 {@code 0}，
     * 而「未设置」是 {@code Integer.MIN_VALUE}，两者<b>不相等</b> —— 若只用
     * 「下一档 == 当前档」来判断「能不能调」，0 级机器会被误判为可调：扳手会白写一次 0，
     * 并弹出「配方优先已被调整为 无」这个什么都没改变的提示。因此调用方必须先用本方法
     * 过滤，再做循环。
     */
    public static boolean isAdjustable(int realTier) {
        if (realTier == CREATIVE_TIER) {
            return true;
        }
        // 带上界：真实等级超出 1..MAX 时按「不可调」拒绝，而不是让 cycle 产出一个
        // 越界的运行等级（cycle 对 from>0 只做 from-1，不设上限）。
        return realTier >= MIN_CYCLE_TIER + 1 && realTier <= MAX_CYCLE_TIER;
    }

    /**
     * 择优权重：越大越优先。这是「同输入多配方选谁」的唯一规则。
     *
     * <ul>
     *   <li><b>偏好等级为创造档</b>（{@code selectionTier == -1}，即创造机器未手动设置）：
     *       创造专用配方（{@code required == -1}）优先，其余按门槛降序 ——
     *       与本模组原有行为一致（既有回归测试锁定）。</li>
     *   <li><b>偏好等级为普通档 0..4</b>：优先「尽可能贴近但不超过该档」的配方，
     *       即玩家把机器调成黄铜就会优先挑黄铜档。</li>
     * </ul>
     *
     * <p>权重区间刻意分离（正数千 / 负数千），保证「≤ 偏好档」的候选永远压过
     * 「> 偏好档」的候选；同组内部再按差距排序。
     */
    public static int selectionWeight(int requiredTier, int selectionTier) {
        if (selectionTier == CREATIVE_TIER) {
            return requiredTier == CREATIVE_TIER ? 10000 : requiredTier;
        }
        if (requiredTier == CREATIVE_TIER) {
            // 普通偏好下创造专用配方没有意义，排到最后。
            return -10000;
        }
        if (requiredTier <= selectionTier) {
            // 落在偏好档及以下：越贴近偏好档越优（精确命中 = 10000）。
            return 10000 - (selectionTier - requiredTier);
        }
        // 高于偏好档：越接近越好，但整体低于「≤ 偏好档」那一组。
        return -1 - (requiredTier - selectionTier);
    }
}
