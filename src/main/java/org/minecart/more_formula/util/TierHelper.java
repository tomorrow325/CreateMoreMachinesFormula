package org.minecart.more_formula.util;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.yxiao233.createmoremachines.api.content.mechanical.deployer.CMMDeployerBlockEntity;
import net.yxiao233.createmoremachines.api.content.mechanical.mixer.CMMMechanicalMixerBlockEntity;
import net.yxiao233.createmoremachines.api.content.mechanical.press.CMMMechanicalPressBlockEntity;
import net.yxiao233.createmoremachines.api.content.spout.CMMSpoutBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.minecart.more_formula.Config;
import org.minecart.more_formula.MachinePriority;
import org.minecart.more_formula.More_formula;
import org.minecart.more_formula.PriorityRules;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TierHelper {

    /**
     * CMM 的等级值与 more_formula 的常量不同号：
     * brass=2 / netherite=3 / end=4 / beyond=5 / creative=-1，
     * 而本模组用 1..4 表示四级、-1 表示创造级。这里做一次平移，
     * {@code <= 1} 的（含 creative 的 -1）原样返回。
     */
    private static final int CMM_TIER_OFFSET = 1;

    /**
     * 可选依赖 CreateMoreMoreMachines 的方块实体包名前缀。只做字符串比较、
     * 不解析任何类：命中前缀才说明该模组确实在场，此时才允许去加载桥接类
     * （见 {@link #externalMachineTier}）。
     */
    private static final String CMMM_PACKAGE = "net.tomorrow325.createmoremoremachines.";
    /** 反射桥接类：内部对 CMMM 类型的直接引用在未安装时绝不能被类加载。 */
    private static final String CMMM_BRIDGE =
            "org.minecart.more_formula.compat.createmoremoremachines.CMMMTierBridge";
    private static volatile Method cmmmTierMethod;
    private static boolean cmmmBridgeWarned;

    /**
     * 机器的<b>真实等级</b>（CMM/CMMM 出厂等级，原版机器为 0）。
     *
     * <p>这个值不受玩家手动设定的「运行等级」影响 —— 处理速度、并行倍率等
     * CMM 自身机制用的也是它，所以调运行等级不会改变那些。
     */
    public static int getRealMachineTier(@Nullable BlockEntity machine) {
        if (machine == null) {
            return 0;
        }
        int cmmTier;
        if (machine instanceof CMMMechanicalPressBlockEntity press) {
            cmmTier = press.getTier().getTierValue();
        } else if (machine instanceof CMMMechanicalMixerBlockEntity mixer) {
            cmmTier = mixer.getTier().getTierValue();
        } else if (machine instanceof CMMDeployerBlockEntity deployer) {
            cmmTier = deployer.getTier().getTierValue();
        } else if (machine instanceof CMMSpoutBlockEntity spout) {
            cmmTier = spout.getTier().getTierValue();
        } else {
            // 原版 Create 机器（或任何非 CMM 方块实体）视为 0 级。
            // 注意这里**不**处理 CMM 的锯：CMM 2.7 没有发布任何锯方块
            // （只有未启用的 API 类），因此不存在需要识别的锯机器。
            // CMMM 的分级破碎轮/锯/鼓风机是可选依赖，经 {@link #externalMachineTier} 识别。
            return externalMachineTier(machine);
        }
        return toFormulaTier(cmmTier);
    }

    /**
     * 玩家的手动「运行等级」；未设置时返回 {@link MachinePriority#UNSET}。
     */
    public static int getRunningTier(@Nullable BlockEntity machine) {
        if (machine == null) {
            return MachinePriority.UNSET;
        }
        return MachinePriority.getRaw(machine);
    }

    /**
     * <b>准入门槛</b>用的等级（决定「哪些配方能做」）。
     *
     * <ul>
     *   <li>未手动设置 → 真实等级（= 与旧行为完全一致，旧存档无变化）。</li>
     *   <li>已手动设置 → 该运行等级（因此调低后做不了高于它的配方）。</li>
     *   <li><b>创造级机器例外</b>：准入恒为创造级（-1），即<b>不受手动值影响</b> ——
     *       需求确认过「创造机器可调，但不影响准入、只影响择优」。</li>
     * </ul>
     */
    public static int getAdmissionTier(@Nullable BlockEntity machine) {
        int real = getRealMachineTier(machine);
        if (real == Config.CREATIVE_TIER) {
            return Config.CREATIVE_TIER;
        }
        int run = getRunningTier(machine);
        return run == MachinePriority.UNSET ? real : run;
    }

    /**
     * <b>择优偏好</b>用的等级（决定「同一输入的多个配方选哪条」）。
     *
     * <p>与准入等级的区别只在创造级机器：创造机器的准入不受限，但择优仍按手动值
     * （所以把创造机器调成黄铜，它会优先挑黄铜档的配方，而不是挑最高的）。
     *
     * <p>未手动设置时返回真实等级；创造级真实等级为 -1，恰好表达
     * 「只把创造专用配方排在前面、其余按等级降序」这一原有的择优行为。
     */
    public static int getSelectionTier(@Nullable BlockEntity machine) {
        int run = getRunningTier(machine);
        if (run != MachinePriority.UNSET) {
            return run;
        }
        return getRealMachineTier(machine);
    }

    /**
     * @deprecated 语义已被拆分，请按用途改用 {@link #getAdmissionTier}（准入）
     * 或 {@link #getSelectionTier}（择优）。保留它只是为了不破坏既有调用点的编译。
     */
    @Deprecated
    public static int getMachineTier(@Nullable BlockEntity machine) {
        return getAdmissionTier(machine);
    }

    /**
     * 识别可选依赖 CreateMoreMoreMachines 的机器（分级破碎轮/锯/鼓风机）并换算等级。
     *
     * <p>为什么不直接 instanceof：本类在主 mixin 配置（不门控）里被引用，
     * 任何对 CMMM 类型的直接引用都会在未安装该模组时抛 NoClassDefFoundError。
     * 因此先用「类名前缀 + 反射」判断 —— 前缀不匹配（= 模组未安装或原版机器）时
     * 只花一次字符串比较，绝不触碰 CMMM 的类；命中后才加载
     * {@code CMMMTierBridge}（该类只在前缀命中时才会被加载，内部才能安全地
     * 直接引用 CMMM 类型）。
     */
    /**
     * 纯函数：给定类名是否落在可选依赖 CreateMoreMoreMachines 的包名下。
     * 前缀未命中（含 {@code null}/空串）时返回 {@code false}，且**绝不**触碰桥接类。
     *
     * <p>设为 public 是刻意的离线测试缝：{@code externalMachineTier} 需要
     * BlockEntity 实例（离线构造不可行），而这条「前缀未命中 → 0、不加载桥接」的
     * 半边规则可以脱离游戏进程用字符串直接断言。
     */
    public static boolean isCMMMClassName(String className) {
        return className != null && className.startsWith(CMMM_PACKAGE);
    }

    private static int externalMachineTier(BlockEntity machine) {
        if (!isCMMMClassName(machine.getClass().getName())) {
            return 0;
        }
        try {
            Method bridge = cmmmTierMethod;
            if (bridge == null) {
                bridge = Class.forName(CMMM_BRIDGE, true, TierHelper.class.getClassLoader())
                        .getMethod("getMachineTier", BlockEntity.class);
                cmmmTierMethod = bridge;
            }
            return (Integer) bridge.invoke(null, machine);
        } catch (Throwable t) {
            // 桥接失败按「原版机器」处理（拒绝带门槛的配方），只警告一次避免刷屏。
            if (!cmmmBridgeWarned) {
                cmmmBridgeWarned = true;
                More_formula.LOGGER.warn("more_formula: failed to resolve CreateMoreMoreMachines tier for {}, treating as tier 0",
                        machine.getClass().getName(), t);
            }
            return 0;
        }
    }

    /** CMM/CMMM 等级值 → 本模组等级值。等级语义见类注释，纯函数便于离线回归。 */
    public static int toFormulaTier(int cmmTier) {
        if (cmmTier <= 1) {
            return cmmTier;
        }
        return cmmTier - CMM_TIER_OFFSET;
    }

    /**
     * 核心门槛判定（纯函数）：{@code required <= 0} 之外，
     * 创造级（-1）机器通行一切，其余机器要求 {@code tier >= required}。
     */
    public static boolean isTierAllowed(int machineTier, int requiredTier) {
        if (requiredTier == -1) {
            return machineTier == -1;
        }
        if (requiredTier <= 0) {
            return true;
        }
        return machineTier == -1 || machineTier >= requiredTier;
    }

    public static boolean isAllowed(@Nullable BlockEntity machine, ResourceLocation recipeId) {
        int required = Config.getRequiredTier(recipeId);
        int tier = getAdmissionTier(machine);
        return isTierAllowed(tier, required);
    }

    // =========================================================================
    //  「同输入多配方」择优
    // =========================================================================
    //
    // 背景：Create 各机器选配方统一走
    //     AllRecipeTypes.find(input, level)
    //       → RecipeManager.getRecipeFor(type, input, level)
    //       → byType(type).stream().filter(matches).findFirst()
    // 也就是「取**注册顺序里第一条**匹配的配方」，与门槛完全无关。
    //
    // 于是同一输入写了 t1 / t2 两条 crushing 时，先注册的那条（t1）永远先命中；
    // 旧实现只在返回值上问「这一条允许吗」—— 命中 t1 就放行 t1。用户可见的症状正是
    // 「给下界粉碎轮喂料，出来的却是黄铜粉碎轮的产物」。
    //
    // 正确语义：候选集合 = 该输入下**所有**匹配的配方；先滤掉这台机器等级不够的；
    // 剩下的取门槛**最高**的那条。全部不够 → 空（闸门拦截，与原行为一致）。
    // 这样黄铜机器仍只做得了 t1，而下界机器会主动选 t2。
    // =========================================================================

    /**
     * 门槛在「择优」时的排序权重。
     *
     * <p>常规等级就是其本身（越大越高级）。创造级（-1）是**独占**档而非阶梯顶端，
     * 但在一台创造级机器上，若同一输入同时存在「常规最高级」和「创造专用」两条配方，
     * 更该执行的是那条创造专用的 —— 因此把创造级排在所有常规等级之上。
     */
    public static int selectionRank(int requiredTier) {
        return requiredTier == Config.CREATIVE_TIER ? Integer.MAX_VALUE : requiredTier;
    }

    /** 配方门槛（薄封装，便于离线测试与统一调用点）。 */
    public static int requiredTierOf(ResourceLocation recipeId) {
        return Config.getRequiredTier(recipeId);
    }

    /**
     * 从候选里挑出该机器**实际应执行**的那一条：滤掉等级不够的，余下取门槛最高者。
     * 并列时保留候选顺序里的第一条（贴近原版 findFirst 的确定性）。
     */
    public static <T extends Recipe<?>> Optional<RecipeHolder<T>> pickBestAllowed(
            @Nullable BlockEntity machine, @Nullable List<RecipeHolder<T>> candidates) {
        // 择优同时受两件事约束：
        //  - 准入：够不着的配方不能选（用 getAdmissionTier）
        //  - 偏好：够得着的里面优先挑「运行等级」那一档（用 getSelectionTier）
        // 两者对普通机器是同一个值；只有创造级机器会分叉（准入恒为 -1、偏好随手动值）。
        // 实现上取「准入等级」做过滤、用「偏好等级」做排序权重，因此这里传偏好等级，
        // 但过滤那一步必须用准入等级 —— 见 pickBestByTiers。
        return pickBestByTiers(getAdmissionTier(machine), getSelectionTier(machine), candidates);
    }

    /**
     * 择优内核（同时吃「准入等级」与「偏好等级」两个值）。
     *
     * <p>对普通机器两个参数相同；创造级机器准入恒为 -1（通行一切）、偏好随手动值。
     */
    public static <T extends Recipe<?>> Optional<RecipeHolder<T>> pickBestByTiers(
            int admissionTier, int selectionTier, @Nullable List<RecipeHolder<T>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        int bestIndex = pickBestIndexByTiers(admissionTier, selectionTier, recipeIds(candidates));
        return bestIndex < 0 ? Optional.empty() : Optional.ofNullable(candidates.get(bestIndex));
    }

    /**
     * 最底层的纯函数：只吃「配方 id 列表」，返回选中项的下标；都不够时返回 -1。
     *
     * <p>刻意不碰任何 Minecraft 类型，便于离线断言 —— {@code Recipe} 的静态初始化
     * 要求游戏注册表已 bootstrap，离线构造 {@code RecipeHolder} 会抛
     * "Not bootstrapped"。
     */
    public static int pickBestIndexByTiers(
            int admissionTier, int selectionTier, @Nullable List<ResourceLocation> ids) {
        // 与排序内核共用同一套规则，避免两条路径发散。
        List<Integer> ranked = rankIndicesForTiers(admissionTier, selectionTier, ids);
        return ranked.isEmpty() ? -1 : ranked.get(0);
    }

    /**
     * 纯函数内核：给定机器等级与候选配方，挑出应执行的那条。
     *
     * <p>抽出来是为了能离线断言 —— {@link #pickBestAllowed} 需要 BlockEntity 实例
     * （离线构造不出来），而选择规则本身只跟「机器等级」这个整数有关。
     */
    public static <T extends Recipe<?>> Optional<RecipeHolder<T>> pickBestAllowedForTier(
            int machineTier, @Nullable List<RecipeHolder<T>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        int bestIndex = pickBestIndexForTier(machineTier, recipeIds(candidates));
        return bestIndex < 0 ? Optional.empty() : Optional.ofNullable(candidates.get(bestIndex));
    }

    /**
     * 最底层的纯函数：只吃「配方 id 列表」，返回选中项的下标；都不够时返回 -1。
     *
     * <p>这一层刻意不碰任何 Minecraft 类型 —— {@code Recipe} 的静态初始化要求游戏
     * 注册表已 bootstrap（{@code BuiltInRegistries.<clinit>} 会抛
     * "Not bootstrapped"），因此离线测试<b>无法</b>构造 {@code RecipeHolder}。
     * 把规则下沉到「下标 + id 字符串」这一层，就能在纯 JVM 里断言选择逻辑。
     */
    public static int pickBestIndexForTier(int machineTier, @Nullable List<ResourceLocation> ids) {
        List<Integer> ranked = rankIndicesForTier(machineTier, ids);
        return ranked.isEmpty() ? -1 : ranked.get(0);
    }

    /**
     * 纯函数：给定机器等级与候选配方 id，返回「这台机器可以执行」的候选下标，
     * 并按门槛<b>从高到低</b>排好序；够不着的候选不出现。
     *
     * <p>这是所有「顺序即选择」机器的共同内核：
     * <ul>
     *   <li>取单条的机器（压机、破碎轮）：用返回序列的<b>第 0 个</b>；</li>
     *   <li>取列表的机器（锯、工作盆 {@code list.get(0)}）：按返回序列重排整个列表。</li>
     * </ul>
     *
     * <p>排序是<b>稳定</b>的：门槛相同时保留下标的小到大顺序（= 原候选顺序），
     * 因此不会打乱 Create 自己编码在顺序里的其它偏好（例如工作盆按配料数排序）。
     * 只有当门槛确实不同时，取用结果才会改变。
     *
     * <p>刻意不碰任何 Minecraft 类型，便于离线断言 —— {@code Recipe} 的静态初始化
     * 要求游戏注册表已 bootstrap，离线构造 {@code RecipeHolder} 会抛
     * "Not bootstrapped"。
     */
    public static List<Integer> rankIndicesForTier(int machineTier, @Nullable List<ResourceLocation> ids) {
        // 单参数的兼容入口：准入与偏好取同一个值（对普通机器就是真实情况）。
        return rankIndicesForTiers(machineTier, machineTier, ids);
    }

    /**
     * 排序内核（准入 + 偏好两个等级）：滤掉够不着的，其余按「偏好权重」降序稳定排序。
     *
     * <p>给「取列表第 0 项」或「按下标取用」的机器用（锯、工作盆）。
     */
    public static List<Integer> rankIndicesForTiers(
            int admissionTier, int selectionTier, @Nullable List<ResourceLocation> ids) {
        List<Integer> out = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        // 收集「够得着」的候选下标，保持原顺序。
        // null id = 查不到门槛（例如反查失败）→ 按「无门槛」处理，与旧行为一致。
        List<Integer> allowed = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (isTierAllowed(admissionTier, requiredOrZero(ids.get(i)))) {
                allowed.add(i);
            }
        }
        // 按偏好权重降序稳定排序：同权重保留原下标顺序（= Create 原有的相对偏好）。
        allowed.sort((ia, ib) -> Integer.compare(
                selectionWeight(requiredOrZero(ids.get(ib)), selectionTier),
                selectionWeight(requiredOrZero(ids.get(ia)), selectionTier)));
        out.addAll(allowed);
        return out;
    }

    /**
     * 择优权重：越大越优先。这是「同输入多配方选谁」的唯一规则。
     *
     * <ul>
     *   <li><b>偏好等级为创造档</b>（{@code selectionTier == -1}，即创造机器未手动设置）：
     *       创造专用配方（{@code required == -1}）优先，其余按门槛降序。
     *       这与本模组原有的择优行为一致（既有回归测试锁定）。</li>
     *   <li><b>偏好等级为普通档 0..4</b>：优先「尽可能贴近但不超过该档」的配方 ——
     *       即玩家把机器调成黄铜，就会优先挑黄铜档的配方。
     *       创造专用配方排最后（普通机器本来就做不了它）。</li>
     * </ul>
     *
     * <p>权重区间刻意分离（正数千 / 负数千），保证「≤ 偏好档」的候选永远压过
     * 「> 偏好档」的候选；同档内部再按差距排序。
     */
    public static int selectionWeight(int requiredTier, int selectionTier) {
        // 规则本体在 PriorityRules（纯函数、可离线断言），这里只转发以免两处漂移。
        return PriorityRules.selectionWeight(requiredTier, selectionTier);
    }

    private static <T extends Recipe<?>> List<ResourceLocation> recipeIds(List<RecipeHolder<T>> candidates) {
        List<ResourceLocation> ids = new ArrayList<>(candidates.size());
        for (RecipeHolder<T> holder : candidates) {
            ids.add(holder == null ? null : holder.id());
        }
        return ids;
    }

    private static int requiredOrZero(@Nullable ResourceLocation id) {
        return id == null ? 0 : Config.getRequiredTier(id);
    }

    /**
     * 把「同一输入的多个候选」按等级降序就地重排，并滤掉该机器等级不够的。
     *
     * <p>给「取列表第 0 项」的机器用（工作盆 {@code updateBasin} 用
     * {@code list.get(0)} 定 currentRecipe）。排序是稳定的：同等级的候选保持
     * Create 原有的相对顺序，因此只在等级确实不同时才改变取用结果。
     *
     * <p>工作盆拿到的是 {@code List<Recipe<?>>}（没有 RecipeHolder），配方 id 需要
     * 反查；反查不到（{@code id == null}）时按「无门槛」处理并保留在列表里 ——
     * 与原行为一致。
     */
    public static List<Recipe<?>> sortMatchingByTier(Level level, BlockEntity machine, List<Recipe<?>> recipes) {
        List<Recipe<?>> out = new ArrayList<>();
        if (recipes == null || recipes.isEmpty()) {
            return out;
        }
        int machineTier = getAdmissionTier(machine);
        int preferTier = getSelectionTier(machine);
        // 反查每个候选的 id（反查不到 → null，按无门槛处理）。
        List<ResourceLocation> ids = new ArrayList<>(recipes.size());
        for (Recipe<?> recipe : recipes) {
            ids.add(findRecipeId(level, recipe));
        }
        // 复用同一个纯函数内核：滤掉够不着的 + 按偏好权重降序稳定排序。
        List<Integer> order = rankIndicesForTiers(machineTier, preferTier, ids);
        for (int i : order) {
            out.add(recipes.get(i));
        }
        return out;
    }

    /**
     * 该输入下**所有**匹配的配方（不按门槛过滤）。
     *
     * <p>Create 的 {@code find} 只回一条（{@code byType().filter(matches).findFirst()}），
     * 这里要的是全集，所以直接问 {@code RecipeManager.getRecipesFor} —— 它内部同样是
     * {@code byType(type).filter(matches)}，用同一个 {@code matches} 判定，只是不 findFirst。
     */
    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> matchingRecipes(
            Level level, RecipeType<T> type, I input) {
        return level.getRecipeManager().getRecipesFor(type, input, level);
    }

    /**
     * 破碎轮控制器的完整候选池：**先 crushing、后 milling**。
     *
     * <p>顺序刻意如此：本模组择优时并列取「候选里第一条」，而 Create 原生优先级是
     * 「有 crushing 就不看 milling」。两者合起来 = 同门槛时仍优先 crushing，
     * 但**门槛更高的一方会胜出** —— 这正是本次修复要的行为（同一输入写了 t1/t2 两条
     * crushing 时，等级够的机器必须执行 t2，而不是先注册的 t1）。
     *
     * <p>为什么用裸类型 + 强转：{@code findRecipe} 的声明类型是
     * {@code StandardProcessingRecipe<RecipeWrapper>}，而 crushing/milling 的实际类继承
     * {@code StandardProcessingRecipe<RecipeInput>}（见 6.0.10 的
     * {@code AbstractCrushingRecipe}）。泛型擦除后是同一个类，Create 自己在
     * {@code AllRecipeTypes.find} 里也是这么强转的，这里照做。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> crushingCandidates(
            Level level, RecipeWrapper input) {
        List<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> out = new ArrayList<>();
        collectInto(out, level, AllRecipeTypes.CRUSHING, input);
        collectInto(out, level, AllRecipeTypes.MILLING, input);
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectInto(List<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>> out,
                                    Level level, AllRecipeTypes type, RecipeWrapper input) {
        List<RecipeHolder<?>> found = level.getRecipeManager().getRecipesFor((RecipeType) type.getType(), input, level);
        for (RecipeHolder<?> holder : found) {
            out.add((RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>) holder);
        }
    }

    /**
     * 该输入下所有匹配的 milling 配方（磨石用）。
     *
     * <p>返回类型写 {@code AbstractCrushingRecipe} 是刻意的：{@code MillingRecipe}
     * 继承自它，而磨石 {@code process()} 里原调用返回的就是
     * {@code Optional<RecipeHolder<MillingRecipe>>}；泛型擦除后都是
     * {@code RecipeHolder}，择优逻辑只读 {@code holder.id()}，因此用哪个上界都安全。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<RecipeHolder<com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe>> millingRecipes(
            Level level, RecipeWrapper input) {
        List<RecipeHolder<com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe>> out = new ArrayList<>();
        List<RecipeHolder<?>> found = level.getRecipeManager()
                .getRecipesFor((RecipeType) AllRecipeTypes.MILLING.getType(), input, level);
        for (RecipeHolder<?> holder : found) {
            out.add((RecipeHolder<com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe>) holder);
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    public static ResourceLocation findRecipeId(Level level, Recipe<?> recipe) {
        RecipeType type = recipe.getType();
        for (Object entry : level.getRecipeManager().getAllRecipesFor(type)) {
            RecipeHolder<?> holder = (RecipeHolder<?>) entry;
            if (holder.value() == recipe) {
                return holder.id();
            }
        }
        return null;
    }

    public static boolean isFillingAllowed(BlockEntity machine, Level level, ItemStack stack, @Nullable FluidStack availableFluid) {
        int required = getRequiredFillingTier(level, stack, availableFluid);
        int tier = getAdmissionTier(machine);
        return isTierAllowed(tier, required);
    }

    /**
     * 取出「这台喷口可能执行的浇注配方」所要求的门槛。
     *
     * <p>为什么取**最大**门槛而不是「第一个匹配到的配方的门槛」：
     * Create 自己选配方用的是 {@code RecipeManager.getRecipesFor(...)} 的返回顺序，
     * 取第一个流体匹配的配方（见 {@code FillingBySpout#fillItem}）。
     * 这个顺序不受我们控制，同一物品 + 同一流体命中多条配方时，
     * 「取第一条」很可能和 Create 实际执行的那条不是同一条 —— 旧实现就有这个漏洞：
     * 它先查序列装配展开出的配方、再查普通浇注配方，任取其一返回，
     * 一旦被取到的那条没设门槛（返回 0），整台机器就会直接放行，
     * 而 Create 真正执行的却可能是设了门槛的那条。
     *
     * <p>因此这里对**所有候选**取最大门槛：只要候选里存在设了门槛的配方，
     * 就按最严的那条来判定。代价是「同一物品+流体同时存在有门槛和无门槛配方」时
     * 会偏严；但本模组的职责就是拦截，宁可拦得严，也不能漏放。
     */
    private static int getRequiredFillingTier(Level level, ItemStack stack, @Nullable FluidStack availableFluid) {
        SingleRecipeInput input = new SingleRecipeInput(stack);
        int required = 0;

        // 序列装配展开出的浇注配方（Create 的 canItemBeFilled 优先查这一组）
        for (RecipeHolder<FillingRecipe> holder : SequencedAssemblyRecipe.getRecipes(
                level, stack, AllRecipeTypes.FILLING.getType(), FillingRecipe.class, r -> true)) {
            FillingRecipe recipe = holder.value();
            if (availableFluid != null && !recipe.getRequiredFluid().test(availableFluid)) {
                continue;
            }
            required = Math.max(required, Config.getRequiredTier(holder.id()));
        }

        // 普通浇注配方
        for (RecipeHolder<Recipe<SingleRecipeInput>> holder : level.getRecipeManager()
                .getRecipesFor(AllRecipeTypes.FILLING.getType(), input, level)) {
            if (!(holder.value() instanceof FillingRecipe recipe)) {
                continue;
            }
            if (availableFluid != null && !recipe.getRequiredFluid().test(availableFluid)) {
                continue;
            }
            required = Math.max(required, Config.getRequiredTier(holder.id()));
        }

        return required;
    }
}
