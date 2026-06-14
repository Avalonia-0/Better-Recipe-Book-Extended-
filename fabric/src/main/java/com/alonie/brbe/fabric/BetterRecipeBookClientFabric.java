package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.fabric.compat.rei.ReiCompatHandler;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class BetterRecipeBookClientFabric implements ClientModInitializer {
    private final Set<Screen> registeredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onInitializeClient() {
        // Register platform-specific providers
        PlatformPotionUtilImpl.init();

        // Register optional compat handlers
        ReiCompatHandler.register();
        ModMenuReflectiveBridge.register();
        
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            this.registeredScreens.remove(screen);
            // Apply overlay hide state immediately when screen opens (no flash)
            OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen screen = client.screen;
            if (BetterRecipeBook.config.hideReiJeiOverlay && screen != null) {
                OverlayHider.ensureJeiOverlayHidden();
            }
            if (screen == null || this.registeredScreens.contains(screen) || !TopLayerOverlayRenderer.hasOverlay(screen)) {
                return;
            }

            this.registeredScreens.add(screen);
            ScreenEvents.afterExtract(screen).register(TopLayerOverlayRenderer::render);
        });
    }
}
