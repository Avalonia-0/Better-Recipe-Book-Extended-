package com.alonie.brbe.neoforge;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.platform.client.ConfigurationScreenRegistry;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.neoforge.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import me.shedaniel.autoconfig.AutoConfigClient;
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

        // Register configuration screen for NeoForge built-in mod menu
        ConfigurationScreenRegistry.register(
                Platform.getMod(BetterRecipeBook.MOD_ID),
                parent -> AutoConfigClient.getConfigScreen(Config.class, parent).get()
        );

        ClientGuiEvent.INIT_POST.register((screen, firstInit) -> {
            if (screen != null) {
                registeredScreens.remove(screen);
                // Apply overlay hide state immediately when screen opens (no flash)
                OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
            }
        });

        ClientTickEvent.CLIENT_POST.register(client -> {
            Screen screen = client.screen;
            // Per-tick JEI state enforcement — reads real JEI state, no internal tracking
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
