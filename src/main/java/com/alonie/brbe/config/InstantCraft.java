package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "instantCraft")
public class InstantCraft implements ConfigData {
    @ConfigEntry.Gui.PrefixText
    public boolean showButton = true;
    public boolean enabled = false;

}
