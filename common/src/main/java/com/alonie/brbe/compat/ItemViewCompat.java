package com.alonie.brbe.compat;

import net.minecraft.world.item.ItemStack;

/**
 * Self-contained bridge for JEI/REI recipe/usage view key handling.
 * <p>
 * Stores a single {@code handler} object (either {@code JeiCompat.JeiHandler}
 * or {@code ReiCompat.ReiHandler}) and invokes its {@code openRecipeView} /
 * {@code openUsageView} methods reflectively. This avoids compile-time and
 * class-loading dependencies on {@code JeiCompat} / {@code ReiCompat} so that
 * mixin classes which call {@link #isLoaded()} do not trigger
 * {@link NoClassDefFoundError} when JEI/REI classes are absent from the
 * classloader.
 */
public final class ItemViewCompat {

    private static Object handler;

    private ItemViewCompat() {}

    /** Called by {@code JeiCompat} or {@code ReiCompat} when a handler is ready. */
    public static void setHandler(Object h) {
        handler = h;
    }

    public static boolean isLoaded() {
        return handler != null;
    }

    public static boolean openRecipeView(ItemStack stack) {
        if (handler == null || stack.isEmpty()) return false;
        try {
            return (boolean) handler.getClass()
                    .getMethod("openRecipeView", ItemStack.class)
                    .invoke(handler, stack);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean openUsageView(ItemStack stack) {
        if (handler == null || stack.isEmpty()) return false;
        try {
            return (boolean) handler.getClass()
                    .getMethod("openUsageView", ItemStack.class)
                    .invoke(handler, stack);
        } catch (Exception e) {
            return false;
        }
    }
}
