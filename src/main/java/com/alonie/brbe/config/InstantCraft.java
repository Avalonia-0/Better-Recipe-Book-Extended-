package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

/** 一键制作（instantCraft）子配置。
 *  <p>注：{@code showButton} 原有的 {@code @PrefixText}「§e一键制作」文字行已按用户要求移除，
 *  其界面位置也由 {@code ConfigTipsHelper.relocateEntries} 改到「界面」页顶部。</p> */
@Config(name = "instantCraft")
public class InstantCraft implements ConfigData {
    public boolean showButton = true;
    public boolean enabled = false;

}
