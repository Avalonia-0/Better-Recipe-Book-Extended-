package com.alonie.brbe.util;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;

public class RecipeMenuUtil {

    public static boolean isRecipeSlot(RecipeBookMenu menu, int slot) {
        if (menu instanceof AbstractFurnaceMenu) {
            return AbstractFurnaceMenu.INGREDIENT_SLOT == slot;
        } else {
            return isCraftingGridSlot(menu, slot);
        }
    }

    public static boolean isCraftingGridSlot(RecipeBookMenu menu, int slot) {
        if (menu instanceof AbstractCraftingMenu craftingMenu) {
            return craftingMenu.getInputGridSlots().stream().anyMatch(inputSlot -> inputSlot.index == slot);
        }

        return slot > 0 && slot < menu.slots.size();
    }

    public static boolean isResultSlot(RecipeBookMenu menu, int slot) {
        if (menu instanceof AbstractCraftingMenu craftingMenu) {
            return craftingMenu.getResultSlot().index == slot;
        }

        return slot == 0;
    }

    public static boolean isCraftingMenuSlot(RecipeBookMenu menu, int slot) {
        return isCraftingGridSlot(menu, slot) || isResultSlot(menu, slot);
    }

}
