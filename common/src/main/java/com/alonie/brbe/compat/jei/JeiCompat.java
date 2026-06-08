package com.alonie.brbe.compat.jei;

import com.alonie.brbe.compat.ItemViewCompat;
import net.minecraft.world.item.ItemStack;

public final class JeiCompat {

    private JeiCompat() {}

    public static void setHandler(JeiHandler h) {
        ItemViewCompat.setHandler(h);
    }

    public static boolean isLoaded() {
        return ItemViewCompat.isLoaded();
    }

    public static boolean openRecipeView(ItemStack stack) {
        return ItemViewCompat.openRecipeView(stack);
    }

    public static boolean openUsageView(ItemStack stack) {
        return ItemViewCompat.openUsageView(stack);
    }

    public interface JeiHandler extends ItemViewCompat.Handler {
        // inherits openRecipeView(ItemStack) and openUsageView(ItemStack)
    }
}
