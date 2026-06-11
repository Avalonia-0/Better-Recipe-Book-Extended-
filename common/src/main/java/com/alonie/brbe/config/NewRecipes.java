package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "newRecipes")
public class NewRecipes implements ConfigData {
    @ConfigEntry.Gui.Tooltip()
    public boolean unlockAll = true;
    @ConfigEntry.Gui.Tooltip()
    public boolean enableBounce = false;
}
