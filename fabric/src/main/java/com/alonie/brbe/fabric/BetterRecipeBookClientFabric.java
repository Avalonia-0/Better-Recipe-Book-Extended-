package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import com.alonie.brbe.impl.hud.JeiHudHider;
import com.alonie.brbe.impl.hud.ReiHudHider;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.fabric.FabricPlatform;
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

        // Register HUD hiders (JEI + REI overlay control)
        OverlayHider.register(new JeiHudHider());
        OverlayHider.register(new ReiHudHider());

        // Initialize RBIP platform (Fabric)
        RecipeBookIsPain.PLATFORM = new FabricPlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
        RecipeBookIsPain.LOGGER.info("[RBIP] Fabric platform initialized");

        // Register optional compat handlers
        ReiCompat.register();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            this.registeredScreens.remove(screen);
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
            ScreenEvents.afterRender(screen).register(TopLayerOverlayRenderer::render);
        });
    }
}
