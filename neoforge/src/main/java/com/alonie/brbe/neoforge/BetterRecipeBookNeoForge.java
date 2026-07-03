package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.Config;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(BetterRecipeBook.MOD_ID)
public final class BetterRecipeBookNeoForge {
    public BetterRecipeBookNeoForge(ModContainer container, IEventBus modEventBus) {
        BetterRecipeBook.init();
        BetterRecipeBookClientNeoForge.init(modEventBus);

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) ->
                        AutoConfigClient.getConfigScreen(Config.class, parent).get()
        );
    }
}
