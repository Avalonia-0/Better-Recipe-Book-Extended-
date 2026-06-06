package com.alonie.brbe.loaders;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.BrewableResult;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.List;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getPotionMixes;

public class PotionLoader {
    public static List<BrewableResult> POTIONS = new ArrayList<>();

    public static void init() {
        ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register(PotionLoader::load);
        LifecycleEvent.SERVER_LEVEL_UNLOAD.register((clientLevel) -> PotionLoader.clear());
    }

    private static void load(ClientLevel level) {
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
