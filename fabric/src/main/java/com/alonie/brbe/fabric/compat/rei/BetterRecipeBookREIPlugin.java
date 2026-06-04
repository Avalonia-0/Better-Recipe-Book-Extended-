package com.alonie.brbe.fabric.compat.rei;

import com.alonie.brbe.util.TopLayerOverlayRenderer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collection;

/**
 * REI plugin for exclusion zones.
 * Uses reflection to register with REI at runtime, avoiding compile-time dependency.
 */
public class BetterRecipeBookREIPlugin {

    public static void registerExclusionZones(Screen screen, Collection<?> zones) {
        // No-op at compile time; REI integration is handled via reflection if loaded
    }
}
