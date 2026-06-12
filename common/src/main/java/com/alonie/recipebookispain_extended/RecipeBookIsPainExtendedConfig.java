package com.alonie.recipebookispain_extended;

import com.alonie.brbe.BetterRecipeBook;

/**
 * Bridges RBIP's config access to BRBE's Cloth Config.
 * {@code extendedFeatures()} returns {@code enableRecipeBookIsPain},
 * {@code bottomNumber()} returns 16 or 6 based on {@code enableTabPage}.
 * <p>
 * Values are read live from {@code BetterRecipeBook.config.rbip} so
 * toggling them in the config screen takes effect immediately (hot reload).
 */
public final class RecipeBookIsPainExtendedConfig {
    private static final int MIN_BOTTOM_NUMBER = 6;
    private static final int MAX_BOTTOM_NUMBER = 16;
    private static final int DEFAULT_BOTTOM_NUMBER = 16;

    private final boolean extendedFeatures;
    private final int bottomNumber;

    private RecipeBookIsPainExtendedConfig(boolean extendedFeatures, int bottomNumber) {
        this.extendedFeatures = extendedFeatures;
        this.bottomNumber = bottomNumber;
    }

    public boolean extendedFeatures() {
        return this.extendedFeatures;
    }

    public int bottomNumber() {
        return this.bottomNumber;
    }

    public static RecipeBookIsPainExtendedConfig get() {
        if (BetterRecipeBook.config != null) {
            boolean ext = BetterRecipeBook.config.rbip.enableRecipeBookIsPain;
            int bottom = BetterRecipeBook.config.rbip.enableTabPage ? DEFAULT_BOTTOM_NUMBER : MIN_BOTTOM_NUMBER;
            return new RecipeBookIsPainExtendedConfig(ext, bottom);
        }
        // Fallback before config is loaded (shouldn't normally reach here)
        return new RecipeBookIsPainExtendedConfig(true, DEFAULT_BOTTOM_NUMBER);
    }

    /**
     * No-op: config is always live from BRBE's in-memory config object.
     * Kept for API compatibility with callers that expect this method.
     */
    public static boolean reloadIfChanged() {
        return false;
    }
}
