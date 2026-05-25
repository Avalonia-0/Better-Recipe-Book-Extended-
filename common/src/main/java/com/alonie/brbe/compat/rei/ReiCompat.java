package com.alonie.brbe.compat.rei;

import net.minecraft.world.item.ItemStack;

/**
 * Bridge for REI (Roughly Enough Items) integration.
 * Uses a handler pattern so common code can call REI API without REI being on the classpath.
 * The handler is registered by the Fabric client initializer if REI is loaded.
 */
public class ReiCompat {
    private static ReiHandler handler;

    public static void setHandler(ReiHandler h) {
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

    public interface ReiHandler {
        boolean openRecipeView(ItemStack stack);
        boolean openUsageView(ItemStack stack);
    }
}
