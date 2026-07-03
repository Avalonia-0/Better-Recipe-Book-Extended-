package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.Config;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(BetterRecipeBook.MOD_ID)
public final class BetterRecipeBookNeoForge {
    public BetterRecipeBookNeoForge(ModContainer container, IEventBus modEventBus) {
        BetterRecipeBook.init();
        BetterRecipeBookClientNeoForge.init(modEventBus);

        // Cloth Config not yet available for 26.2
        try {
            var autoConfigClient = Class.forName("me.shedaniel.autoconfig.AutoConfigClient");
            var getConfigScreenMethod = autoConfigClient.getMethod("getConfigScreen", Class.class, net.minecraft.client.gui.screens.Screen.class);
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (modContainer, parent) -> {
                        try {
                            return (net.minecraft.client.gui.screens.Screen) getConfigScreenMethod.invoke(null, Config.class, parent);
                        } catch (Exception ignored) {
                            return parent;
                        }
                    }
            );
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.info("[BRBE] Cloth Config not available — NeoForge config screen disabled");
        }
    }
}
