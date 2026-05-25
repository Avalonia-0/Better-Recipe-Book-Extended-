package com.alonie.brbe.fabric.compat.rei;

import com.alonie.brbe.util.TopLayerOverlayRenderer;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collection;
import java.util.Collections;

public class BetterRecipeBookREIPlugin implements REIClientPlugin {
    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(Screen.class, BetterRecipeBookREIPlugin::provideOverlayExclusionZones);
    }

    private static Collection<Rectangle> provideOverlayExclusionZones(Screen screen) {
        ScreenRectangle bounds = TopLayerOverlayRenderer.getOverlayBounds(screen);
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new Rectangle(bounds.left(), bounds.top(), bounds.width(), bounds.height()));
    }
}
