package com.alonie.brbe;

import com.alonie.brbe.util.ClientInventoryUtil;
import com.alonie.brbe.util.RecipeMenuUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

public class InstantCraftingManager {
    public Object lastInstantCraftButton = null;

    private boolean awaitingResultSlotUpdate;
    private boolean craftAll;
    private boolean applyingInstantCraft;
    private int containerId = -1;
    private RecipeDisplayId lastClickedRecipe;

    public void recipeClicked(RecipeDisplayId recipe, boolean craftAll) {
        if (!this.isEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }

        if (!(minecraft.player.containerMenu instanceof RecipeBookMenu menu) || menu instanceof AbstractFurnaceMenu) {
            return;
        }

        this.lastClickedRecipe = recipe;
        this.awaitingResultSlotUpdate = true;
        this.craftAll = craftAll;
        this.containerId = menu.containerId;
    }

    public void onResultSlotUpdated(ItemStack itemStack) {
        if (!this.awaitingResultSlotUpdate || this.applyingInstantCraft || itemStack == null || itemStack.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            this.clearPending();
            return;
        }

        if (!(minecraft.player.containerMenu instanceof RecipeBookMenu menu) || menu.containerId != this.containerId) {
            this.clearPending();
            return;
        }

        this.applyingInstantCraft = true;
        try {
            if (!menu.getCarried().isEmpty()) {
                ClientInventoryUtil.storeItem(-1, idx -> !RecipeMenuUtil.isCraftingMenuSlot(menu, idx));
            }

            if (this.craftAll) {
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, minecraft.player);
            } else {
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.PICKUP, minecraft.player);
                ClientInventoryUtil.storeItem(-1, idx -> !RecipeMenuUtil.isCraftingMenuSlot(menu, idx));
            }
        } finally {
            this.applyingInstantCraft = false;
            this.clearPending();
        }
    }

    public boolean toggleEnabled() {
        BetterRecipeBook.config.instantCraft.enabled = !BetterRecipeBook.config.instantCraft.enabled;
        BetterRecipeBook.configHolder.save();
        return isEnabled();
    }

    public boolean isEnabled() {
        return BetterRecipeBook.config.instantCraft.enabled;
    }

    private void clearPending() {
        this.awaitingResultSlotUpdate = false;
        this.craftAll = false;
        this.containerId = -1;
    }
}
