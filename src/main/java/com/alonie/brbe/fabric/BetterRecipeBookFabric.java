package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import net.fabricmc.api.ModInitializer;

public class BetterRecipeBookFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BetterRecipeBook.init();
    }
}
