package com.alonie.brbe.neoforge;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
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
        ClientGuiEvent.INIT_POST.register((screen, firstInit) -> {
            if (screen != null) {
                registeredScreens.remove(screen);
            }
        });

        ClientTickEvent.CLIENT_POST.register(client -> {
            Screen screen = client.screen;
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
