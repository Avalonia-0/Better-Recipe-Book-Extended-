package com.alonie.brbe.fabric;

import com.alonie.brbe.fabric.compat.rei.ReiCompatHandler;
import net.fabricmc.api.ClientModInitializer;

public class BetterRecipeBookClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ReiCompatHandler.register();
    }
}
