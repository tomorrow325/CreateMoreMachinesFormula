package org.minecart.more_formula.compat.kubejs.wrapper;

import org.minecart.more_formula.Config;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public class MoreFormulaKubeJSConfig {

    // 这三个入口从服务器脚本（server_scripts）调用，因此登记为 SERVER 来源：
    // 每次 /reload 前会被清空后由脚本重新写入，不会跨重载累积。
    public static void setTier(String recipeId, int tier) {
        if (recipeId.endsWith("*")) {
            Config.addPrefixTier(recipeId, tier, Config.Source.SERVER);
        } else {
            Config.addTier(ResourceLocation.parse(recipeId), tier, Config.Source.SERVER);
        }
    }

    public static void setPrefixTier(String prefix, int tier) {
        Config.addPrefixTier(prefix, tier, Config.Source.SERVER);
    }

    public static void removeTier(String recipeId) {
        // 与 setTier 对称：* 结尾走前缀移除，绝不能把 * 喂给 parse（会抛异常）。
        if (recipeId.endsWith("*")) {
            Config.removePrefixTier(recipeId);
        } else {
            Config.removeTier(ResourceLocation.parse(recipeId));
        }
    }

    public static int getTier(String recipeId) {
        return Config.getRequiredTier(ResourceLocation.parse(recipeId));
    }

    public static Map<ResourceLocation, Integer> getAllTiers() {
        return Config.getAllTiers();
    }

    public static List<Integer> getKnownTiers() {
        return Config.getKnownTiers();
    }

}
