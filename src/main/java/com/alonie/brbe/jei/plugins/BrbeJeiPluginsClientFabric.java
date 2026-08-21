package com.alonie.brbe.jei.plugins;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class BrbeJeiPluginsClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1.21.11 有真实 JEI（无 vendored mezz 兜底）：JEI 未加载时不能触发
        // 收集逻辑（会 NoClassDefFoundError），必须在此守卫。
        if (!FabricLoader.getInstance().isModLoaded("jei")) return;
        // Collect after a level loads, matching JEI's AFTER_RECIPES_UPDATED
        // timing — data components are bound by then, so plugins like
        // Farmer's Delight that build ItemStacks in registerRecipeCatalysts work.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BrbeJeiPlugins.collectAndInject());
    }
}
