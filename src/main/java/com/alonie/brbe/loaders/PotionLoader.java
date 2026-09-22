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

        BrbeLogger.log("BRBE", "Loaded %d potions.".formatted(POTIONS.size()));
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

    private static void clearNoLog() {
        POTIONS.clear();
    }
}
