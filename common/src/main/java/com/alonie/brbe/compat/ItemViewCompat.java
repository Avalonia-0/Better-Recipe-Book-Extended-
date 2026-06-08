package com.alonie.brbe.compat;

import com.alonie.brbe.compat.jei.JeiCompat;
import com.alonie.brbe.compat.rei.ReiCompat;
import net.minecraft.world.item.ItemStack;

public final class ItemViewCompat {
    private ItemViewCompat() {}

    public static boolean isLoaded() {
        return JeiCompat.isLoaded() || ReiCompat.isLoaded();
    }

    public static boolean openRecipeView(ItemStack stack) {
        return JeiCompat.openRecipeView(stack) || ReiCompat.openRecipeView(stack);
    }

    public static boolean openUsageView(ItemStack stack) {
        return JeiCompat.openUsageView(stack) || ReiCompat.openUsageView(stack);
    }
}
