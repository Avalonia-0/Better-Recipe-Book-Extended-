package com.alonie.brbe.neoforge;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.neoforge.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.impl.hud.JeiHudHider;
import com.alonie.brbe.impl.hud.ReiHudHider;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.neoforge.NeoForgePlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * NeoForge client initializer.
 * Ported from Fabric's BetterRecipeBookClientFabric using Architectury cross-platform events.
 */
public class BetterRecipeBookClientNeoForge {

    private static final Set<Screen> registeredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    public static void init() {
        // Register platform provider
        PlatformPotionUtilImpl.init();

        // Register HUD hiders (JEI + REI overlay control)
        OverlayHider.register(new JeiHudHider());
        OverlayHider.register(new ReiHudHider());

        // Initialize RBIP platform (was previously in RBIPNeoForgeMod)
        RecipeBookIsPain.PLATFORM = new NeoForgePlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
        RecipeBookIsPain.LOGGER.info("[RBIP] NeoForge platform initialized, isOwOLoaded={}", RecipeBookIsPain.isOwOLoaded);

        // Defer REI compat registration until client starts (after all mods are loaded).
        // Also unconditionally force overlays visible — setOverlaysHidden(false) is
        // a no-op on fresh start (currentlyHidden=false), so we use forceShowOverlays()
        // which bypasses all guards and directly sets JEI/REI to visible via reflection.
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> {
            ReiCompat.register();
            OverlayHider.forceShowOverlays();
            // Then apply the persisted config state (normally a no-op since default is false)
            if (BetterRecipeBook.config != null) {
                OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
            }
        });

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen != null) {
                registeredScreens.remove(screen);
                // Apply overlay hide state immediately when screen opens (no flash)
                if (BetterRecipeBook.config != null) {
                    OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
                }
            }
        });

        ClientTickEvent.CLIENT_POST.register(client -> {
            Screen screen = client.screen;
            // Per-tick JEI state enforcement — reads real JEI state, no internal tracking
            if (BetterRecipeBook.config != null
                    && BetterRecipeBook.config.hideReiJeiOverlay
                    && screen != null) {
                OverlayHider.ensureJeiOverlayHidden();
            }
            if (screen == null || registeredScreens.contains(screen) || !TopLayerOverlayRenderer.hasOverlay(screen)) {
                return;
            }

            registeredScreens.add(screen);
            ClientGuiEvent.RENDER_POST.register((scr, guiGraphics, mouseX, mouseY, delta) -> {
                if (scr == screen) {
                    TopLayerOverlayRenderer.render(screen, guiGraphics, mouseX, mouseY, delta);
                }
            });
        });
    }
}
