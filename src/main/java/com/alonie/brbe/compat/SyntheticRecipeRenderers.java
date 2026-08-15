package com.alonie.brbe.compat;

/**
 * Static holder for the {@link SyntheticRecipeRenderer} registered by the
 * companion mod (brbe-jei-plugins).  The default is a no-op, so the front-end
 * degrades to its static rendering when the companion mod is absent.
 */
public final class SyntheticRecipeRenderers {

    private static volatile SyntheticRecipeRenderer instance = SyntheticRecipeRenderer.NONE;

    private SyntheticRecipeRenderers() {}

    public static void register(SyntheticRecipeRenderer renderer) {
        instance = renderer != null ? renderer : SyntheticRecipeRenderer.NONE;
    }

    public static SyntheticRecipeRenderer get() {
        return instance;
    }
}
