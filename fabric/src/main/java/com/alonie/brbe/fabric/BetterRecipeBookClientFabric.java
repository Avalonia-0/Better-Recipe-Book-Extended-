package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import dev.architectury.platform.Platform;
import dev.architectury.platform.client.ConfigurationScreenRegistry;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
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
        // Register platform provider
        PlatformPotionUtilImpl.init();

        // Register config screen via Architectury so setOptionFunction hook is applied
        ConfigurationScreenRegistry.register(
                Platform.getMod(BetterRecipeBook.MOD_ID),
                parent -> {
                    java.util.function.Supplier<Screen> supplier =
                            AutoConfigClient.getConfigScreen(Config.class, parent);
                    if (!OverlayHider.isApplicable() && supplier instanceof ConfigScreenProvider<?> provider) {
                        provider.setOptionFunction((configId, field) -> {
                            if ("hideReiJeiOverlay".equals(field.getName())) return null;
                            return "option." + configId + "." + field.getName();
                        });
                    }
                    return supplier.get();
                }
        );

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            this.registeredScreens.remove(screen);
            // Apply overlay hide state immediately when screen opens (no flash)
            OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen screen = client.screen;
            // Per-tick JEI state enforcement — reads real JEI state, no internal tracking
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
