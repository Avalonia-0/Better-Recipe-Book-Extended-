package com.alonie.brbe.fabric.compat.rei;

import com.alonie.brbe.util.TopLayerOverlayRenderer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collection;

public class BetterRecipeBookREIPlugin {

    public static void registerExclusionZones(Screen screen, Collection<?> zones) {
        // No-op; REI integration handled via ReiCompatHandler reflection
    }
}
