package com.alonie.brbe.api.config;

import net.minecraft.client.gui.screens.Screen;

/**
 * A factory that creates a config screen given a parent screen.
 *
 * <p>Used by platform-specific entry points to register the mod's config
 * screen with ModMenu (Fabric) or equivalent (NeoForge) without pulling in
 * compile-time dependencies on any config-screen API.
 */
@FunctionalInterface
public interface ConfigScreenProvider {
    Screen create(Screen parent);
}
