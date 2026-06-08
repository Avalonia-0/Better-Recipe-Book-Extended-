package com.alonie.brbe.fabric;

import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.config.Config;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;

import java.util.function.Function;

/**
 * ModMenu integration.
 * Uses ModMenu API via reflection at runtime where possible; otherwise provides config screen directly.
 */
public class ModMenuFabric {
    public static Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> getConfigScreenFactory() {
        return parent -> {
            java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> supplier =
                    AutoConfigClient.getConfigScreen(Config.class, parent);
            // Hide hideReiJeiOverlay option when neither JEI nor REI is installed
            if (!OverlayHider.isApplicable() && supplier instanceof ConfigScreenProvider<?> provider) {
                provider.setOptionFunction((configId, field) -> {
                    if ("hideReiJeiOverlay".equals(field.getName())) return null;
                    return "option." + configId + "." + field.getName();
                });
            }
            return supplier.get();
        };
    }
}
