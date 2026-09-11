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
    public static final int MODE_ANVIL = 4;
    public static final int MODE_BREWING = 5;
    public static final int MODE_GRINDSTONE = 6;

    private static float current = -1f;
    private static int mode = -1;
    private static int selIdx = 0;

    private PinButtonRenderOverride() {}

    /** Enter pin rendering: the cloned button renders with the pin's frozen
     *  zoom, layout mode AND slot-select index — the index is pinned here so
     *  the button's Alt state comes from the pin (window-independent). */
    public static void push(float scale, int pinMode, int pinSelIdx) {
        current = scale;
        mode = pinMode;
        selIdx = pinSelIdx;
    }

    public static void pop() {
        current = -1f;
        mode = -1;
        selIdx = 0;
    }

    public static boolean active() {
        return current >= 0f;
    }

    public static float current() {
        return current;
    }

    /** The pinned slot-select index (the pin's own Alt state). */
    public static int selIdx() {
        return selIdx;
    }

    /** The pin's frozen layout mode (raw MODE_* value — the mixin's mode()
     *  helper only maps the three classic modes, so anvil / brewing /
     *  grindstone pins must read the override directly). */
    public static int mode() {
        return mode;
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
