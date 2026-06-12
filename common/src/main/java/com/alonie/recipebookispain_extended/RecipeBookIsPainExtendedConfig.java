package com.alonie.recipebookispain_extended;

import com.alonie.brbe.BetterRecipeBook;

/**
 * Bridges RBIP's config access to BRBE's Cloth Config.
 * All features previously gated by {@code extendedFeatures} are now
 * always active. The {@code enableRecipeBookIsPain} switch controls
 * the entire RBIP module, and {@code enableTabPage} controls the
 * tab page count (16 or 6).
 * <p>
 * Values are read live from {@code BetterRecipeBook.config.rbip} so
 * toggling them in the config screen takes effect immediately.
 */
public final class RecipeBookIsPainExtendedConfig {

    private static int reloadGeneration;

    private RecipeBookIsPainExtendedConfig() {}

    public static boolean enabled() {
        if (BetterRecipeBook.config == null) return true;
        return BetterRecipeBook.config.rbip.enableRecipeBookIsPain;
    }

    public static int bottomNumber() {
        if (BetterRecipeBook.config == null) return 16;
        return BetterRecipeBook.config.rbip.enableTabPage ? 16 : 6;
    }

    /** Call when BRBE config is saved to signal a hot reload. */
    public static void requestReload() {
        reloadGeneration++;
    }

    /** Returns the current reload generation for change detection. */
    public static int reloadGeneration() {
        return reloadGeneration;
    }

    /** @deprecated All features are now core; always returns true. */
    @Deprecated
    public boolean extendedFeatures() { return true; }

    public static RecipeBookIsPainExtendedConfig get() { return Holder.INSTANCE; }
    private static final class Holder { static final RecipeBookIsPainExtendedConfig INSTANCE = new RecipeBookIsPainExtendedConfig(); }

    /** No-op: config is live. */
    public static boolean reloadIfChanged() { return false; }
}
