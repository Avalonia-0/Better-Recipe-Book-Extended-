package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.Config;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(BetterRecipeBook.MOD_ID)
public final class BetterRecipeBookNeoForge {
    public BetterRecipeBookNeoForge(ModContainer container) {
        BetterRecipeBook.init();
        BetterRecipeBookClientNeoForge.init();

        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> {
                    java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> supplier =
                            AutoConfigClient.getConfigScreen(Config.class, parent);
                    if (supplier instanceof ConfigScreenProvider<?> provider) {
                        provider.setOptionFunction((configId, field) -> {
                            if ("enableRecipeBookIsPain".equals(field.getName())) return null;
                            return "option." + configId + "." + field.getName();
                        });
                    }
                    return supplier.get();
                }
        );
    }
}
