package com.alonie.brbe.loaders;


import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.BrewableResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.alchemy.Potion;
import java.util.ArrayList;
import java.util.List;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getPotionMixes;

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

        BetterRecipeBook.LOGGER.info("Loaded %d potions.".formatted(POTIONS.size()));
    }

    public static void clear() {
        BetterRecipeBook.LOGGER.info("Clearing potions...");
        clearNoLog();
    }

    private static void clearNoLog() {
        POTIONS.clear();
    }
}
