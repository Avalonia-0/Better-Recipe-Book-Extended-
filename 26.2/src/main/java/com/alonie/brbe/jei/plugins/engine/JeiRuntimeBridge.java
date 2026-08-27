package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.recipe.IRecipeManager;

/**
 * Holds the real JEI {@link IRecipeManager} once JEI's runtime is available, so
 * the synthetic recipe renderer can delegate the full recipe UI (category
 * background, slot backgrounds, drawables, animations) to JEI itself instead of
 * re-implementing JEI's category rendering.  Set by
 * {@code BetterRecipeBookJEIPlugin#onRuntimeAvailable}; null when JEI is absent
 * or not yet ready.
 */
public final class JeiRuntimeBridge {

    private JeiRuntimeBridge() {}

    private static IRecipeManager recipeManager;

    public static void set(IRecipeManager manager) {
        recipeManager = manager;
    }

    public static void clear() {
        recipeManager = null;
    }

    public static IRecipeManager recipeManager() {
        return recipeManager;
    }
}
