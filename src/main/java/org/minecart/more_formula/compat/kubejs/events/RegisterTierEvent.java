package org.minecart.more_formula.compat.kubejs.events;

import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.event.KubeEvent;
import org.minecart.more_formula.Config;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class RegisterTierEvent implements KubeEvent {
    private final Map<ResourceLocation, Integer> exactTiers = new HashMap<>();
    private final Map<String, Integer> prefixTiers = new HashMap<>();

    public void setTier(String recipeId, int tier) {
        if (recipeId.endsWith("*")) {
            prefixTiers.put(recipeId, tier);
        } else {
            setTier(ResourceLocation.parse(recipeId), tier);
        }
    }

    public void setTier(ResourceLocation recipeId, int tier) {
        exactTiers.put(recipeId, tier);
    }

    public void removeTier(String recipeId) {
        // 与 MoreFormula.removeTier 同一套通配语义：* 结尾删前缀条目，否则删精确条目。
        if (recipeId.endsWith("*")) {
            Config.removePrefixTier(recipeId);
        } else {
            removeTier(ResourceLocation.parse(recipeId));
        }
    }

    public void removeTier(ResourceLocation recipeId) {
        Config.removeTier(recipeId);
    }

    public int getTier(String recipeId) {
        return getTier(ResourceLocation.parse(recipeId));
    }

    public int getTier(ResourceLocation recipeId) {
        Integer tier = exactTiers.get(recipeId);
        return tier != null ? tier : Config.getRequiredTier(recipeId);
    }

    public Map<ResourceLocation, Integer> getAllTiers() {
        Map<ResourceLocation, Integer> all = new HashMap<>(Config.getAllTiers());
        all.putAll(exactTiers);
        return all;
    }

    @Override
    public void afterPosted(EventResult result) {
        // 这个事件由 STARTUP 脚本触发，且只在游戏加载完成时投递一次；
        // 即便有人从服务器脚本里调它，写进 STARTUP 桶也不会被 /reload 清掉。
        for (var entry : exactTiers.entrySet()) {
            Config.addTier(entry.getKey(), entry.getValue(), Config.Source.STARTUP);
        }
        for (var entry : prefixTiers.entrySet()) {
            Config.addPrefixTier(entry.getKey(), entry.getValue(), Config.Source.STARTUP);
        }
    }
}
