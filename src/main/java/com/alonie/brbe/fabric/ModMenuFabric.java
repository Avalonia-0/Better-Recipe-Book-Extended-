package com.alonie.brbe.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.util.ConfigTipsHelper;

public class ModMenuFabric implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // 走 ConfigTipsHelper：ModMenu 的配置按钮同样要拿到整理过的界面
        return parent -> ConfigTipsHelper.buildConfigScreen(BrbeConfig.class, parent);
    }
}
