package com.alonie.brbe.compat.jei;

import net.minecraft.world.item.ItemStack;

public final class JeiCompat {
    private static JeiHandler handler;

    private JeiCompat() {
    }

    public static void setHandler(JeiHandler h) {
        handler = h;
    }

    public static boolean isLoaded() {
        return handler != null;
    }

    public static boolean openRecipeView(ItemStack stack) {
        if (handler != null) {
            return handler.openRecipeView(stack);
        }
        return false;
    }

    public static boolean openUsageView(ItemStack stack) {
        if (handler != null) {
            return handler.openUsageView(stack);
        }
        return false;
    }

    public interface JeiHandler {
        boolean openRecipeView(ItemStack stack);

        boolean openUsageView(ItemStack stack);
    }
}
