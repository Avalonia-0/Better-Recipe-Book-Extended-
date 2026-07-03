package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.neoforge.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.neoforge.NeoForgePlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * NeoForge client initializer using native NeoForge events.
 * No Architectury API dependency.
 */
public class BetterRecipeBookClientNeoForge {

    private static final Set<Screen> registeredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    public static void init(IEventBus modEventBus) {

        // Register key mappings (F = pin recipe)
        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(BetterRecipeBook.PIN_MAPPING);
        });
        // Register platform provider
        PlatformPotionUtilImpl.init();

        // Register PotionLoader lifecycle hooks (was in Architectury ClientLifecycleEvent.CLIENT_LEVEL_LOAD)
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel().isClientSide() && event.getLevel() instanceof ClientLevel clientLevel) {
                PotionLoader.load(clientLevel);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                PotionLoader.clear();
            }
        });

        // Initialize RBIP platform (was previously in RBIPNeoForgeMod)
        RecipeBookIsPain.PLATFORM = new NeoForgePlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
        RecipeBookIsPain.LOGGER.info("[RBIP] NeoForge platform initialized, isOwOLoaded={}", RecipeBookIsPain.isOwOLoaded);

        // Register REI compat (was deferred to CLIENT_STARTED via Architectury)
        ReiCompat.register();

        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            Screen screen = event.getScreen();
            if (screen != null) {
                registeredScreens.remove(screen);
                // Apply overlay hide state immediately when screen opens (no flash)
                OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
            }
        });

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft client = Minecraft.getInstance();
            Screen screen = client.screen;
            // Per-tick JEI state enforcement — reads real JEI state, no internal tracking
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
