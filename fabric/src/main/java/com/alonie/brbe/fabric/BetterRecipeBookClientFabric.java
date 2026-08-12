package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.impl.hud.JeiHudHider;
import com.alonie.brbe.impl.hud.ReiHudHider;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class BetterRecipeBookClientFabric implements ClientModInitializer {
    private final Set<Screen> registeredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onInitializeClient() {
        // Register key mappings (previously in common via Architectury)
        KeyMappingHelper.registerKeyMapping(BetterRecipeBook.PIN_MAPPING);
        KeyMappingHelper.registerKeyMapping(BetterRecipeBook.RECIPE_VIEW_MAPPING);
        KeyMappingHelper.registerKeyMapping(BetterRecipeBook.USAGE_VIEW_MAPPING);

        // Register platform-specific providers
        PlatformPotionUtilImpl.init();

        // Register PotionLoader lifecycle hooks (was in Architectury ClientLifecycleEvent.CLIENT_LEVEL_LOAD)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.level != null) PotionLoader.load(client.level);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PotionLoader.clear();
        });

        // Register optional compat handlers
        com.alonie.brbe.fabric.compat.rei.ReiCompatHandler.register();
        ModMenuReflectiveBridge.register();

        // Register HUD hiders (JEI + REI overlay control)
        OverlayHider.register(new JeiHudHider());
        OverlayHider.register(new ReiHudHider());
        
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            this.registeredScreens.remove(screen);
            // Apply overlay hide state immediately when screen opens (no flash)
            OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen screen = client.gui.screen();
            if (BetterRecipeBook.config.hideReiJeiOverlay && screen != null) {
                OverlayHider.ensureJeiOverlayHidden();
            }
            if (screen == null || this.registeredScreens.contains(screen) || !TopLayerOverlayRenderer.hasOverlay(screen)) {
                return;
            }

            this.registeredScreens.add(screen);
            ScreenEvents.afterExtract(screen).register(TopLayerOverlayRenderer::render);
        });

        // Register built-in resource pack (Unique Dark filter textures)
        ResourceLoader.registerBuiltinPack(
                Identifier.fromNamespaceAndPath("brbe", "brbe_unique_dark"),
                FabricLoader.getInstance().getModContainer("brbe").orElseThrow(),
                Component.literal("Unique Dark Lite ").append(Component.literal("✕").withStyle(ChatFormatting.YELLOW)).append(Component.literal(" BRBE")),
                PackActivationType.NORMAL);
    }
}
