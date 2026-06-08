package com.alonie.brbe.neoforge;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.neoforge.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * NeoForge client initializer for 1.21.1.
 */
public class BetterRecipeBookClientNeoForge {

    private static final Set<Screen> registeredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    public static void init() {
        // Register platform provider
        PlatformPotionUtilImpl.init();

        // Defer REI compat registration until client starts (after all mods are loaded)
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> ReiCompat.register());

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen != null) {
                registeredScreens.remove(screen);
                OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
            }
        });

        ClientTickEvent.CLIENT_POST.register(client -> {
            Screen screen = client.screen;
            if (BetterRecipeBook.config.hideReiJeiOverlay && screen != null) {
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
