package com.alonie.brbe.mixins.unlockrecipes;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.util.ClientInventoryUtil;
import com.alonie.brbe.util.RecipeMenuUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    protected abstract void startPrediction(ClientLevel arg, PredictiveAction arg2);

    @Shadow
    private int carriedIndex;

    @Inject(method = "handlePlaceRecipe", at = @At(value = "HEAD"), cancellable = true)
    public void onPlaceRecipe(int z, RecipeDisplayId recipe, boolean shiftKeyDown, CallbackInfo ci) {
        if (BetterRecipeBook.config.newRecipes.unlockAll && minecraft.player != null && minecraft.gameMode != null &&
                minecraft.screen instanceof AbstractRecipeBookScreen<?> screen && minecraft.player.containerMenu instanceof RecipeBookMenu menu) {
            RecipeBookComponent<?> comp = ((AbstractRecipeBookScreenAccessor) screen).betterRecipeBook$getRecipeBookComponent();

            RecipeBookPage page = ((RecipeBookComponentAccessor) comp).getRecipeBookPage();
            RecipeCollection lastRecipe = page.getLastClickedRecipeCollection();
            if (lastRecipe == null) {
                return;
            }

            // if we don't have all the items place a client side ghost recipe, then cancel the server request
            if (!lastRecipe.isCraftable(recipe)) {
                // remove items from the crafting grid: not all backends do this for us if we haven't unlocked the recipe
                for (int i = 0; i < menu.slots.size(); i++) {
                    if (!RecipeMenuUtil.isCraftingMenuSlot(menu, i)) continue;
                    ClientInventoryUtil.storeItem(i, idx -> !RecipeMenuUtil.isCraftingMenuSlot(menu, idx));
                }

                // place the ghost recipe
                for (RecipeDisplayEntry entry : lastRecipe.getRecipes()) {
                    if (entry.id().equals(recipe)) {
                        comp.fillGhostRecipe(entry.display());
                        break;
                    }
                }

                // don't send recipe requests to the server
                ci.cancel();
            }
        }
    }

}
