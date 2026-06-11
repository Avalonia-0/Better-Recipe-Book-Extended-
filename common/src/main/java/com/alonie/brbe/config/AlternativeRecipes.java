package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "alternativeRecipes")
public class AlternativeRecipes implements ConfigData {
    @ConfigEntry.Gui.Tooltip()
    public boolean onHover = true;
    public boolean noGrouped = false;
}
