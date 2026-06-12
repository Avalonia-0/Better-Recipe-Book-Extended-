package com.alonie.brbe.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Mixin configuration plugin for Recipe Book is Pain Extended (jar-in-jar).
 * <p>
 * Reads BRBE's Cloth Config TOML file at {@code config/brbe.toml} and checks
 * {@code enableRecipeBookIsPain}. If false, RBIP's mixins are skipped entirely.
 * This runs at class-loading time, before Cloth Config itself is initialized.
 * <p>
 * Only active on the client side (RBIP is client-only).
 */
public final class RbipMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String CONFIG_PATH = "config/brbe.toml";
    private static final String ENABLED_MARKER = "enableRecipeBookIsPain = true";
    private static boolean rbipEnabled = true;

    static {
        Path configFile = Path.of(CONFIG_PATH);
        if (Files.exists(configFile)) {
            try {
                String content = Files.readString(configFile);
                rbipEnabled = content.contains(ENABLED_MARKER);
            } catch (IOException e) {
                // If we can't read the config, default to enabled
                rbipEnabled = true;
            }
        }
        // If config file doesn't exist yet, defaults apply (enabled = true)
    }

    @Override
    public void onLoad(String mixinPackage) {
        // No-op
    }

    @Override
    @Nullable
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return rbipEnabled;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No-op
    }

    @Override
    @Nullable
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op
    }
}
