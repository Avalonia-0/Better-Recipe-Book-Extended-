package com.alonie.brbe.fabric;

import com.alonie.brbe.config.Config;
import me.shedaniel.autoconfig.AutoConfigClient;

import java.util.function.Function;

/**
 * ModMenu integration.
 * Uses ModMenu API via reflection at runtime where possible; otherwise provides config screen directly.
 */
public class ModMenuFabric {
    public static Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> getConfigScreenFactory() {
        return parent -> AutoConfigClient.getConfigScreen(Config.class, parent).get();
    }
}
