package com.alonie.brbe.neoforge;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.platform.client.ConfigurationScreenRegistry;
import com.alonie.brbe.BetterRecipeBook;
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
        // Register configuration screen for NeoForge built-in mod menu
        ConfigurationScreenRegistry.register(
                Platform.getMod(BetterRecipeBook.MOD_ID),
                parent -> AutoConfigClient.getConfigScreen(Config.class, parent).get()
        );

        // Defer REI compat registration until client starts (after all mods are loaded)
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> registerReiCompat());

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
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

    private static void registerReiCompat() {
        if (!Platform.isModLoaded("roughlyenoughitems")) return;

        com.alonie.brbe.compat.rei.ReiCompat.setHandler(new com.alonie.brbe.compat.rei.ReiCompat.ReiHandler() {
            @Override
            public boolean openRecipeView(net.minecraft.world.item.ItemStack stack) {
                try {
                    Class<?> clientHelperClass = Class.forName("me.shedaniel.rei.api.client.ClientHelper");
                    Object instance = clientHelperClass.getMethod("getInstance").invoke(null);
                    Class<?> builderClass = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
                    Object builder = builderClass.getMethod("builder").invoke(null);
                    Class<?> entryStacksClass = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
                    Object entryStack = entryStacksClass.getMethod("of", net.minecraft.world.item.ItemStack.class).invoke(null, stack);
                    builderClass.getMethod("addRecipesFor", entryStack.getClass()).invoke(builder, entryStack);
                    return (Boolean) clientHelperClass.getMethod("openView", builderClass).invoke(instance, builder);
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public boolean openUsageView(net.minecraft.world.item.ItemStack stack) {
                try {
                    Class<?> clientHelperClass = Class.forName("me.shedaniel.rei.api.client.ClientHelper");
                    Object instance = clientHelperClass.getMethod("getInstance").invoke(null);
                    Class<?> builderClass = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
                    Object builder = builderClass.getMethod("builder").invoke(null);
                    Class<?> entryStacksClass = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
                    Object entryStack = entryStacksClass.getMethod("of", net.minecraft.world.item.ItemStack.class).invoke(null, stack);
                    builderClass.getMethod("addUsagesFor", entryStack.getClass()).invoke(builder, entryStack);
                    return (Boolean) clientHelperClass.getMethod("openView", builderClass).invoke(instance, builder);
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }
}
