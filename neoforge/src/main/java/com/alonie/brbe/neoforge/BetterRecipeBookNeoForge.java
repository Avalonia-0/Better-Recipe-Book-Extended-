package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.neoforge.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.config.Config;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(BetterRecipeBook.MOD_ID)
public final class BetterRecipeBookNeoForge {
    public BetterRecipeBookNeoForge() {
        PlatformPotionUtilImpl.init();
        BetterRecipeBook.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModContainer container = ModList.get().getModContainerById(BetterRecipeBook.MOD_ID).get();
            IConfigScreenFactory factory = (modContainer, parent) -> {
                java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> supplier =
                        AutoConfig.getConfigScreen(Config.class, parent);
                if (!OverlayHider.isApplicable() && supplier instanceof ConfigScreenProvider<?> provider) {
                    provider.setOptionFunction((configId, field) -> {
                        if ("hideReiJeiOverlay".equals(field.getName())) return null;
                        return "option." + configId + "." + field.getName();
                    });
                }
                return supplier.get();
            };
            container.registerExtensionPoint(IConfigScreenFactory.class, factory);
        }
    }
}
