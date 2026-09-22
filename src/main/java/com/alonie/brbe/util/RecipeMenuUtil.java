package com.alonie.brbe.util;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;

import java.util.function.Predicate;

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
        if (menu instanceof AbstractFurnaceMenu) {
            return false; // Furnaces do not have a crafting grid
        }

        return slot > 0 && slot < menu.slots.size();
    }

    public static boolean isResultSlot(RecipeBookMenu menu, int slot) {
        if (menu instanceof AbstractCraftingMenu craftingMenu) {
            return craftingMenu.getResultSlot().index == slot;
        }
        if (menu instanceof AbstractFurnaceMenu furnaceMenu) {
            return furnaceMenu.getResultSlot().index == slot;
        }

        return slot == 0;
    }

    public static boolean isCraftingMenuSlot(RecipeBookMenu menu, int slot) {
        if (menu instanceof AbstractFurnaceMenu) {
            return false; // Furnace slots are not crafting-menu slots for item-moving purposes
        }
        return isCraftingGridSlot(menu, slot) || isResultSlot(menu, slot);
    }

    /**
     * {@code isCraftingMenuSlot} 的取反谓词（{@link ClientInventoryUtil#storeItem} 用）。
     *
     * <p><b>为什么放在这里而不是调用点</b>：唯一调用点在 mixin 里，而 mixin 内的 lambda
     * 会被编译成合成方法、由 Mixin 重命名并在 latest.log 打一行
     * {@code Renaming synthetic method ...}；普通类里的 lambda 没有这个问题。</p>
     */
    public static Predicate<Integer> notCraftingMenuSlot(RecipeBookMenu menu) {
        return slot -> !isCraftingMenuSlot(menu, slot);
    }

}
