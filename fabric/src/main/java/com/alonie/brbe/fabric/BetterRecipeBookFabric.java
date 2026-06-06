package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import net.fabricmc.api.ModInitializer;

public class BetterRecipeBookFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PlatformPotionUtilImpl.init();
        BetterRecipeBook.init();
    }
}
