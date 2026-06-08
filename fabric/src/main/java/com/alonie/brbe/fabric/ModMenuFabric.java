package com.alonie.brbe.fabric;

import com.alonie.brbe.compat.OverlayHider;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.alonie.brbe.config.Config;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;

public class ModMenuFabric implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            var supplier = AutoConfig.getConfigScreen(Config.class, parent);
            if (!OverlayHider.isApplicable() && supplier instanceof ConfigScreenProvider<?> provider) {
                provider.setOptionFunction((configId, field) -> {
                    if ("hideReiJeiOverlay".equals(field.getName())) return null;
                    return "option." + configId + "." + field.getName();
                });
            }
            return supplier.get();
        };
    }
}
