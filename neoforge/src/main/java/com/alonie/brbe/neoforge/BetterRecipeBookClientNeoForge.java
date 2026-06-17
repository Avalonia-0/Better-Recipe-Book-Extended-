package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.neoforge.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.impl.hud.JeiHudHider;
import com.alonie.brbe.impl.hud.ReiHudHider;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.neoforge.NeoForgePlatform;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * NeoForge client initializer using native NeoForge events.
 * No Architectury API dependency.
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

        // Register on the NeoForge event bus for game events
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            Screen screen = event.getScreen();
            if (screen != null) {
                registeredScreens.remove(screen);
                OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
            }
        });

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Screen screen = net.minecraft.client.Minecraft.getInstance().gui.screen();
            if (BetterRecipeBook.config.hideReiJeiOverlay && screen != null) {
                OverlayHider.ensureJeiOverlayHidden();
            }
            if (screen == null || registeredScreens.contains(screen) || !TopLayerOverlayRenderer.hasOverlay(screen)) {
                return;
            }

            registeredScreens.add(screen);
            NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, renderEvent -> {
                if (renderEvent.getScreen() == screen) {
                    TopLayerOverlayRenderer.render(screen, renderEvent.getGuiGraphics(), renderEvent.getMouseX(), renderEvent.getMouseY(), renderEvent.getPartialTick());
                }
            });
        });
    }
}
