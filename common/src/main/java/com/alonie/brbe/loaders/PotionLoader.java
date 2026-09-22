package com.alonie.brbe.loaders;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.BrewableResult;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.List;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getPotionMixes;

public class PotionLoader {
    public static List<BrewableResult> POTIONS = new ArrayList<>();

    public static void init() {
        // PotionLoader lifecycle registration is now done in platform entry points
        // (BetterRecipeBookClientFabric / BetterRecipeBookClientNeoForge).
        // This no-arg init method only initializes the list.
    }

    public static void load(ClientLevel level) {
        PotionLoader.clearNoLog();

        List<?> MIXES = getPotionMixes(level);

        for (Object potionRecipe : MIXES) {
            POTIONS.add(new BrewableResult(potionRecipe));
        }

        com.alonie.brbe.util.BrbeLogger.log("BRBE", "Loaded {} potions.", POTIONS.size());
    }

    public static void clear() {
        com.alonie.brbe.util.BrbeLogger.log("BRBE", "Clearing potions...");
        clearNoLog();
    }

    private static void clearNoLog() {
        POTIONS.clear();
    }
}
