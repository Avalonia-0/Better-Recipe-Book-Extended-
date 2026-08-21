package com.alonie.brbe.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.alonie.brbe.config.BrbeConfig;
import me.shedaniel.autoconfig.AutoConfig;

public class ModMenuFabric implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfig.getConfigScreen(BrbeConfig.class, parent).get();
    }
}
