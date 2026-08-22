package com.alonie.brbe.jei.plugins;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class BrbeJeiPluginsClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Collect after a level loads, matching JEI's AFTER_RECIPES_UPDATED
        // timing — data components are bound by then, so plugins like
        // Farmer's Delight that build ItemStacks in registerRecipeCatalysts work.
        // mezz.jei.api is vendored (embedded fork), so collection is safe even
        // without the real JEI installed.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BrbeJeiPlugins.collectAndInject());
    }
}
