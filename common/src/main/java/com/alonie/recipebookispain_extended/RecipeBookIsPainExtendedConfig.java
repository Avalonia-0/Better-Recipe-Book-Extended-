package com.alonie.recipebookispain_extended;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads RBIP config directly from {@code config/brbe.toml} using plain
 * string parsing — no external TOML library dependency needed at runtime
 * (Cloth Config's toml4j jar-in-jar is NOT reliably on the classpath
 * across all loaders).
 */
public final class RecipeBookIsPainExtendedConfig {

    private static final Path CONFIG_PATH = Paths.get("config", "brbe.toml");

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

    /** Called every frame from the render hook.  Cheap — only re-reads
     *  the file when its timestamp actually changes. */
    public static boolean reloadIfChanged() {
        try {
            long mod = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            if (mod == lastModified) return false;
            lastModified = mod;
            refresh();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void refreshIfStale() {
        try {
            long mod = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            if (mod != lastModified) { lastModified = mod; refresh(); }
        } catch (Exception ignored) {}
    }

    private static void refresh() {
        boolean inRbip = false;
        try (BufferedReader r = Files.newBufferedReader(CONFIG_PATH)) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("#") || t.isEmpty()) continue;
                if (t.startsWith("[")) { inRbip = t.equals("[rbip]"); continue; }
                if (!inRbip) continue;
                int eq = t.indexOf('=');
                if (eq < 0) continue;
                String key = t.substring(0, eq).trim();
                String val = t.substring(eq + 1).trim();
                switch (key) {
                    case "enableRecipeBookIsPain": cachedEnabled = parseBool(val); break;
                    case "enableTabPage": cachedBottomNumber = parseBool(val) ? 16 : 6; break;
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean parseBool(String s) {
        return "true".equalsIgnoreCase(s);
    }

    /** @deprecated All features are now core; always returns true. */
    @Deprecated
    public boolean extendedFeatures() { return true; }

    public static RecipeBookIsPainExtendedConfig get() { return Holder.INSTANCE; }
    private static final class Holder { static final RecipeBookIsPainExtendedConfig INSTANCE = new RecipeBookIsPainExtendedConfig(); }
}
