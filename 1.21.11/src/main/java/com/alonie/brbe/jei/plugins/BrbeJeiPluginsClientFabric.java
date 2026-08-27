package com.alonie.brbe.jei.plugins;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class BrbeJeiPluginsClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // When the real JEI is absent, boot the embedded full JEI core so the
        // preview/pin popups render the real JEI front-end
        // (createRecipeLayoutDrawable).  No JEI GUI is registered.
        BrbeJeiHeadlessCore.init();

        // Collect after a level loads, matching JEI's AFTER_RECIPES_UPDATED
        // timing — data components are bound by then, so plugins like
        // Farmer's Delight that build ItemStacks in registerRecipeCatalysts work.
        // mezz.jei.api is vendored (embedded full core), so collection is safe
        // even without the real JEI installed.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BrbeJeiPlugins.collectAndInject());
    }
}
