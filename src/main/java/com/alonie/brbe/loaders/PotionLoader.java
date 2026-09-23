package com.alonie.brbe.loaders;


import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.BrewableResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.alchemy.Potion;
import java.util.ArrayList;
import java.util.List;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getPotionMixes;
import com.alonie.brbe.util.BrbeLogger;

public class PotionLoader {
    public static List<BrewableResult> POTIONS = new ArrayList<>();

    public static void init() {
        // PotionLoader lifecycle registration is now done in platform entry points
        // (BetterRecipeBookClientFabric / BetterRecipeBookClientNeoForge).
        // This no-arg init method only initializes the list.
        // Callers should also register the load/clear hooks via the platform events.
    }


    public static void load(ClientLevel level) {
        PotionLoader.clearNoLog();

        List<?> MIXES = getPotionMixes(level);

        for (Object potionRecipe : MIXES) {
            POTIONS.add(new BrewableResult(potionRecipe));
        }

        String tally = formTally();
        BrbeLogger.log("BRBE", tally.isEmpty()
                ? "Loaded %d potions.".formatted(POTIONS.size())
                : "Loaded %d potions (%s).".formatted(POTIONS.size(), tally));
        // 酿造/锻造进度（运行时推导）：重建"材料 → 产物"映射。
        com.alonie.brbe.brewingstand.RecipeUnlockTracker.refreshIngredients();
        // 注：酿造查询引擎数据 = headless-JEI 直接注册（条目自带 native layout，
        // 弹窗/pin 委托完整 JEI UI 无匹配损耗——"数据源定向配方书"的集合构造
        // 版本已撤回：其合成条目无 layout，内容匹配兜底对半失败（实机 92 条目仅
        // 35 挂上 layout）。解锁一致性由 BrewingRecipeCategory.query 门控保证
        // （按产物药水解锁过滤，与酿造书一致）。
    }

    public static void clear() {
        BrbeLogger.log("BRBE", "Clearing potions...");
        clearNoLog();
    }

    /**
     * 按<b>基底物品形态</b>统计（配方书三个标签页各自的条目数）。
     *
     * <p>26.3 的 {@code minecraft:brewing} 配方表把普通/喷溅/滞留三种形态放在同一份
     * 配方里，加载总数是旧版的约 3 倍（旧版 {@code PotionBrewing.Mix} 只有药水→药水）。
     * 这一行是"形态归属是否正确"的现场证据。<b>本分支形态完全未知，返回空串</b>，
     * 日志保持原样。</p>
     */
    private static String formTally() {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (BrewableResult result : POTIONS) {
            net.minecraft.world.item.Item item = result.inputItem();
            String key = item == null ? "unknown"
                    : String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
            counts.merge(key, 1, Integer::sum);
        }
        if (counts.size() == 1 && counts.containsKey("unknown")) return "";
        StringBuilder builder = new StringBuilder();
        counts.forEach((key, count) -> {
            if (builder.length() > 0) builder.append(", ");
            builder.append(key).append('=').append(count);
        });
        return builder.toString();
    }

    private static void clearNoLog() {
        POTIONS.clear();
    }
}
