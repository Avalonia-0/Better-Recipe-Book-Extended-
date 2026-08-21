package com.alonie.brbe.pinoverlay;

/**
 * Temporary override of the recipe-button hover rendering, used by a pin overlay
 * to render its cloned button at the pin's frozen zoom <em>and</em> layout mode
 * instead of the live hover state.  {@code OverlayRecipeButtonMixin} reads
 * {@link #current()} (zoom) and the {@code isFurnace/isStonecutting/isSmithing}
 * mode flags while {@link #active()}.
 */
public final class PinButtonRenderOverride {

    public static final int MODE_CRAFTING = 0;
    public static final int MODE_FURNACE = 1;
    public static final int MODE_STONECUTTING = 2;
    public static final int MODE_SMITHING = 3;

    private static float current = -1f;
    private static int mode = -1;

    private PinButtonRenderOverride() {}

    public static void push(float scale, int pinMode) {
        current = scale;
        mode = pinMode;
    }

    public static void pop() {
        current = -1f;
        mode = -1;
    }

    public static boolean active() {
        return current >= 0f;
    }

    public static float current() {
        return current;
    }

    public static boolean isFurnace() {
        return mode == MODE_FURNACE;
    }

    public static boolean isStonecutting() {
        return mode == MODE_STONECUTTING;
    }

    public static boolean isSmithing() {
        return mode == MODE_SMITHING;
    }
}
