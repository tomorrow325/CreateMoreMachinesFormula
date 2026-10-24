package org.minecart.more_formula;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方 → 机器等级门槛表。
 *
 * <p>按“来源”分桶存放：{@link Source#STARTUP}（启动脚本 / {@code MoreFormulaEvents.registerTier}）
 * 与 {@link Source#SERVER}（服务器脚本里的 {@code .tier()} / {@code MoreFormula.setTier}）。
 * 分桶的意义有两个：
 * <ol>
 *   <li><b>热重载正确</b>：KubeJS 的 {@code /reload} 只会重跑服务器脚本，启动脚本不会重跑。
 *       因此重载时只清空 {@code SERVER} 桶、保留 {@code STARTUP} 桶，
 *       两边都不会累积旧条目（旧版本两个桶混在一起，重载等于无限追加）。</li>
 *   <li><b>优先级明确</b>：精确命中优先于前缀命中；同优先级下 SERVER 优先于 STARTUP；
 *       前缀命中之间“最长前缀优先”。</li>
 * </ol>
 */
public class Config {

    /** 创造级门槛：只有 CMM 的创造级机器可以执行。 */
    public static final int CREATIVE_TIER = -1;
    /** 普通门槛的取值区间 [MIN_TIER, MAX_TIER]，与 CMM 的黄铜..超越四级一一对应。 */
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 4;

    /** 门槛的注册来源，决定它何时被清理。 */
    public enum Source {
        /** 启动脚本注册：整个游戏生命周期内只设置一次，{@code /reload} 不清除。 */
        STARTUP,
        /** 服务器脚本注册：每次 {@code /reload} 重跑前清空，避免反复累积。 */
        SERVER
    }

    private record PrefixTier(String prefix, int tier) {
    }

    private static final Map<Source, Map<ResourceLocation, Integer>> EXACT = new HashMap<>();
    private static final Map<Source, List<PrefixTier>> PREFIX = new HashMap<>();

    static {
        for (Source source : Source.values()) {
            EXACT.put(source, new HashMap<>());
            PREFIX.put(source, new ArrayList<>());
        }
    }

    /** 门槛的合法区间，{@code 0} 表示“不设门槛”，不作为条目保存。 */
    private static boolean isValidTier(int tier) {
        return tier == CREATIVE_TIER || (tier >= MIN_TIER && tier <= MAX_TIER);
    }

    public static int getRequiredTier(ResourceLocation recipeId) {
        if (recipeId == null) {
            return 0;
        }
        // 1) 精确命中：SERVER 优先
        for (Source source : new Source[]{Source.SERVER, Source.STARTUP}) {
            Integer exact = EXACT.get(source).get(recipeId);
            if (exact != null) {
                return exact;
            }
        }
        // 2) 前缀命中：最长前缀优先，等长时 SERVER 优先
        String id = recipeId.toString();
        int bestTier = 0;
        int bestLength = -1;
        for (Source source : new Source[]{Source.SERVER, Source.STARTUP}) {
            for (PrefixTier prefix : PREFIX.get(source)) {
                if (id.startsWith(prefix.prefix()) && prefix.prefix().length() > bestLength) {
                    bestLength = prefix.prefix().length();
                    bestTier = prefix.tier();
                }
            }
        }
        return bestTier;
    }

    /**
     * 所有用到过的等级（精确 + 前缀都算），升序、去重、不含 0。
     * JEI 按这个列表生成分级分类，因此前缀门槛必须出现在这里 —— 旧版本只收集精确条目，
     * 导致「只配了前缀门槛」的等级在 JEI 里完全没有对应分类。
     */
    public static List<Integer> getKnownTiers() {
        List<Integer> all = new ArrayList<>();
        for (Source source : Source.values()) {
            all.addAll(EXACT.get(source).values());
            for (PrefixTier prefix : PREFIX.get(source)) {
                all.add(prefix.tier());
            }
        }
        return all.stream()
                .filter(tier -> tier != 0)
                .distinct()
                .sorted()
                .toList();
    }

    public static void addTier(ResourceLocation recipeId, int tier, Source source) {
        if (recipeId != null && isValidTier(tier)) {
            EXACT.get(source).put(recipeId, tier);
        }
    }

    public static void addPrefixTier(String prefix, int tier, Source source) {
        if (prefix == null || prefix.isEmpty() || !isValidTier(tier)) {
            return;
        }
        if (prefix.endsWith("*")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if (prefix.isEmpty()) {
            return;
        }
        // 同一个前缀重复注册就替换，而不是再追加一条 —— 这是重载不再膨胀的第二道保险。
        final String normalized = prefix;
        List<PrefixTier> list = PREFIX.get(source);
        list.removeIf(existing -> existing.prefix().equals(normalized));
        list.add(new PrefixTier(normalized, tier));
    }

    public static void removeTier(ResourceLocation recipeId) {
        if (recipeId == null) {
            return;
        }
        for (Source source : Source.values()) {
            EXACT.get(source).remove(recipeId);
        }
    }

    /**
     * 删除前缀条目（跨所有来源）。归一化规则与 {@link #addPrefixTier} 相同
     * （去尾部 {@code *}、空串忽略），因此「带 {@code *} 注册、不带 {@code *} 移除」
     * 或反过来都能命中；未命中为无害空操作。
     */
    public static void removePrefixTier(String prefix) {
        if (prefix == null) {
            return;
        }
        if (prefix.endsWith("*")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if (prefix.isEmpty()) {
            return;
        }
        final String normalized = prefix;
        for (Source source : Source.values()) {
            PREFIX.get(source).removeIf(existing -> existing.prefix().equals(normalized));
        }
    }

    /** 删除某来源的全部条目（含前缀）。 */
    public static void clear(Source source) {
        EXACT.get(source).clear();
        PREFIX.get(source).clear();
    }

    /** 清空所有来源。 */
    public static void clearAll() {
        for (Source source : Source.values()) {
            clear(source);
        }
    }

    /** 精确条目（不含前缀），用于查询/展示。 */
    public static Map<ResourceLocation, Integer> getAllTiers() {
        Map<ResourceLocation, Integer> all = new HashMap<>();
        for (Source source : new Source[]{Source.STARTUP, Source.SERVER}) {
            all.putAll(EXACT.get(source));
        }
        return all;
    }
}
