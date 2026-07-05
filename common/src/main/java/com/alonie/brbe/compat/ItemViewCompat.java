package com.alonie.brbe.compat;

import net.minecraft.world.item.ItemStack;

/**
 * Bridge for JEI/REI recipe/usage view key handling.
 * <p>
 * This class is a <b>pure handler container</b> with zero imports of
 * {@code JeiCompat} or {@code ReiCompat}. The actual JEI/REI handler is
 * injected by the platform-specific compat layer
 * ({@code JeiCompat#setHandler} / {@code ReiCompat#setHandler}).
 * <p>
 * This keeps the module dependency one-way: JeiCompat → ItemViewCompat,
 * ReiCompat → ItemViewCompat. Mixin code only references ItemViewCompat
 * and never transitively depends on JEI/REI classes.
 */
public final class ItemViewCompat {

    private static Handler handler;

    private ItemViewCompat() {}

    /** Injected by {@code JeiCompat} or {@code ReiCompat} at runtime. */
    public static void setHandler(Handler h) {
        handler = h;
    }

    public static boolean isLoaded() {
        return handler != null;
    }

    public static boolean openRecipeView(ItemStack stack) {
        return handler != null && handler.openRecipeView(stack);
    }

    public static boolean openUsageView(ItemStack stack) {
        return handler != null && handler.openUsageView(stack);
    }

    /**
     * Check whether the given key press matches the active viewer's
     * "show recipe" binding.  Returns {@code false} when no viewer is
     * loaded or the viewer hasn't implemented key matching.
     */
    public static boolean matchesShowRecipe(int keyCode, int scanCode) {
        return handler != null && handler.matchesShowRecipe(keyCode, scanCode);
    }

    /**
     * Check whether the given key press matches the active viewer's
     * "show uses" binding.
     */
    public static boolean matchesShowUses(int keyCode, int scanCode) {
        return handler != null && handler.matchesShowUses(keyCode, scanCode);
    }

    /** Common interface implemented by both JEI and REI handlers. */
    public interface Handler {
        boolean openRecipeView(ItemStack stack);
        boolean openUsageView(ItemStack stack);

        /**
         * Check whether the given key press matches this viewer's
         * "show recipe" binding.  Default returns {@code false} so
         * existing handlers don't break.
         */
        default boolean matchesShowRecipe(int keyCode, int scanCode) {
            return false;
        }

        /**
         * Check whether the given key press matches this viewer's
         * "show uses" binding.
         */
        default boolean matchesShowUses(int keyCode, int scanCode) {
            return false;
        }
    }
}
