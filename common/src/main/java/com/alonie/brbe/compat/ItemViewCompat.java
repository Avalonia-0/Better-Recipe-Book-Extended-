package com.alonie.brbe.compat;

import com.alonie.brbe.compat.jei.JeiCompat;
import net.minecraft.world.item.ItemStack;

public final class ItemViewCompat {
    private ItemViewCompat() {
    }

    public static boolean isLoaded() {
        return JeiCompat.isLoaded();
    }

    public static boolean openRecipeView(ItemStack stack) {
        return JeiCompat.openRecipeView(stack);
    }

    public static boolean openUsageView(ItemStack stack) {
        return JeiCompat.openUsageView(stack);
    }
}
