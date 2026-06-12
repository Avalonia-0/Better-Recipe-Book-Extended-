package com.alonie.recipebookispain_extended.fabric;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import net.fabricmc.api.ClientModInitializer;

public class RBIPFabricEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RecipeBookIsPain.PLATFORM = new FabricPlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
    }
}
