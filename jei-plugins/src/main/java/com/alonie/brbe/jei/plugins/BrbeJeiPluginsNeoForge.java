package com.alonie.brbe.jei.plugins;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

@Mod("brbe_jei_plugins")
public final class BrbeJeiPluginsNeoForge {

    public BrbeJeiPluginsNeoForge() {
        // Collect after a client world loads, matching JEI's recipe-update
        // timing so data components are bound (Farmer's Delight's plugin
        // builds ItemStacks in registerRecipeCatalysts).
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel().isClientSide()) {
                BrbeJeiPlugins.collectAndInject();
            }
        });
    }
}
