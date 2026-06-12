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

    private RecipeBookIsPainExtendedConfig() {}

    /**
     * Returns whether the RBIP module is enabled.
     * When false, RBIP mixins should return early without modifying anything.
     */
    public static boolean enabled() {
        if (BetterRecipeBook.config == null) return true; // not loaded yet, default on
        return BetterRecipeBook.config.rbip.enableRecipeBookIsPain;
    }

    /**
     * Returns the number of tab slots available for grouping (16 or 6).
     */
    public static int bottomNumber() {
        if (BetterRecipeBook.config == null) return 16;
        return BetterRecipeBook.config.rbip.enableTabPage ? 16 : 6;
    }

    /** @deprecated All features are now core; always returns true. */
    @Deprecated
    public boolean extendedFeatures() { return true; }

    /** @deprecated Use {@link #enabled()} instead for gating the module. */
    @Deprecated
    public int getBottomNumber() { return bottomNumber(); }

    /**
     * Returns a shared singleton for callers that expect the old API.
     */
    public static RecipeBookIsPainExtendedConfig get() { return Holder.INSTANCE; }
    private static final class Holder { static final RecipeBookIsPainExtendedConfig INSTANCE = new RecipeBookIsPainExtendedConfig(); }

    /** No-op: config is live. Kept for API compatibility. */
    public static boolean reloadIfChanged() { return false; }
}
