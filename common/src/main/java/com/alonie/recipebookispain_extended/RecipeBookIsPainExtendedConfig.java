package com.alonie.recipebookispain_extended;

import com.alonie.brbe.BetterRecipeBook;

public final class RecipeBookIsPainExtendedConfig {

    private RecipeBookIsPainExtendedConfig() {}

    public static boolean enabled() {
        if (BetterRecipeBook.config == null) return true;
        return BetterRecipeBook.config.rbip.enableRecipeBookIsPain;
    }

    public static int bottomNumber() {
        if (BetterRecipeBook.config == null) return 16;
        return BetterRecipeBook.config.rbip.enableTabPage ? 16 : 6;
    }

    /** @deprecated All features are now core; always returns true. */
    @Deprecated
    public boolean extendedFeatures() { return true; }

    public static RecipeBookIsPainExtendedConfig get() { return Holder.INSTANCE; }
    private static final class Holder { static final RecipeBookIsPainExtendedConfig INSTANCE = new RecipeBookIsPainExtendedConfig(); }

    private static boolean lastEnabled = true;
    private static int lastBottomNumber = 16;

    public static boolean reloadIfChanged() {
        boolean changed = false;
        boolean current = enabled();
        int currentBottom = bottomNumber();
        if (current != lastEnabled || currentBottom != lastBottomNumber) {
            lastEnabled = current;
            lastBottomNumber = currentBottom;
            changed = true;
        }
        return changed;
    }
}
