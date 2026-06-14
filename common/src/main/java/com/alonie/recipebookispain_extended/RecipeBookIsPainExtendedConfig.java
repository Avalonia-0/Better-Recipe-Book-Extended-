package com.alonie.recipebookispain_extended;

import com.moandjiezana.toml.Toml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads RBIP config directly from {@code config/brbe.toml}, bypassing
 * Cloth Config's Java config object entirely.  This makes hot-reload
 * independent of whether Cloth Config has refreshed its in-memory state.
 */
public final class RecipeBookIsPainExtendedConfig {

    private static final Path CONFIG_PATH = Paths.get("config", "brbe.toml");

    // Cached values — refreshed when the file timestamp changes
    private static long lastModified;
    private static boolean cachedEnabled = true;
    private static int cachedBottomNumber = 16;

    private RecipeBookIsPainExtendedConfig() {}

    public static boolean enabled() {
        refreshIfStale();
        return cachedEnabled;
    }

    public static int bottomNumber() {
        refreshIfStale();
        return cachedBottomNumber;
    }

    public static boolean reloadIfChanged() {
        try {
            long mod = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            if (mod == lastModified) return false;
            lastModified = mod;
            refresh();
            return true;
        } catch (Exception e) {
            return false;  // file missing — no change to report
        }
    }

    private static void refreshIfStale() {
        try {
            long mod = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            if (mod != lastModified) {
                lastModified = mod;
                refresh();
            }
        } catch (Exception ignored) {}
    }

    private static void refresh() {
        try {
            String content = Files.readString(CONFIG_PATH);
            Toml toml = new Toml().read(content);

            Toml rbip = toml.getTable("rbip");
            if (rbip != null) {
                cachedEnabled = rbip.getBoolean("enableRecipeBookIsPain", true);
                cachedBottomNumber = rbip.getBoolean("enableTabPage", true) ? 16 : 6;
            }
        } catch (Exception e) {
            // bad file — keep defaults
        }
    }

    /** @deprecated All features are now core; always returns true. */
    @Deprecated
    public boolean extendedFeatures() { return true; }

    public static RecipeBookIsPainExtendedConfig get() { return Holder.INSTANCE; }
    private static final class Holder { static final RecipeBookIsPainExtendedConfig INSTANCE = new RecipeBookIsPainExtendedConfig(); }
}
