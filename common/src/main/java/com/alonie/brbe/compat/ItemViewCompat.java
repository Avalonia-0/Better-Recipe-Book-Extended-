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

    /** Common interface implemented by both JEI and REI handlers. */
    public interface Handler {
        boolean openRecipeView(ItemStack stack);
        boolean openUsageView(ItemStack stack);
    }
}
