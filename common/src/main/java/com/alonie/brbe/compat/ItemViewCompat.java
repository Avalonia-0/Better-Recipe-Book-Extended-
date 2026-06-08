package com.alonie.brbe.compat;

import net.minecraft.world.item.ItemStack;

/**
 * Bridge for JEI/REI recipe/usage view key handling.
 * Uses {@code Class.forName()} at every call to avoid compile-time and
 * class-loading dependencies on JeiCompat/ReiCompat.
 */
public final class ItemViewCompat {

    private ItemViewCompat() {}

    private static final String JEI_COMPAT = "com.alonie.brbe.compat.jei.JeiCompat";
    private static final String REI_COMPAT = "com.alonie.brbe.compat.rei.ReiCompat";

    public static boolean isLoaded() {
        try {
            Class<?> jc = Class.forName(JEI_COMPAT);
            return (boolean) jc.getMethod("isLoaded").invoke(null);
        } catch (Exception ignored) {}
        try {
            Class<?> rc = Class.forName(REI_COMPAT);
            return (boolean) rc.getMethod("isLoaded").invoke(null);
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean openRecipeView(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            Class<?> jc = Class.forName(JEI_COMPAT);
            if ((boolean) jc.getMethod("isLoaded").invoke(null)) {
                return (boolean) jc.getMethod("openRecipeView", ItemStack.class).invoke(null, stack);
            }
        } catch (Exception ignored) {}
        try {
            Class<?> rc = Class.forName(REI_COMPAT);
            if ((boolean) rc.getMethod("isLoaded").invoke(null)) {
                return (boolean) rc.getMethod("openRecipeView", ItemStack.class).invoke(null, stack);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean openUsageView(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            Class<?> jc = Class.forName(JEI_COMPAT);
            if ((boolean) jc.getMethod("isLoaded").invoke(null)) {
                return (boolean) jc.getMethod("openUsageView", ItemStack.class).invoke(null, stack);
            }
        } catch (Exception ignored) {}
        try {
            Class<?> rc = Class.forName(REI_COMPAT);
            if ((boolean) rc.getMethod("isLoaded").invoke(null)) {
                return (boolean) rc.getMethod("openUsageView", ItemStack.class).invoke(null, stack);
            }
        } catch (Exception ignored) {}
        return false;
    }
}
