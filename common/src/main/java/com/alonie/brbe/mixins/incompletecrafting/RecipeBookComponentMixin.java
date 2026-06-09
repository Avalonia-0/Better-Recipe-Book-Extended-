package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Prevents incompatible (3x3) recipes from being filtered out of the recipe
 * book collection list on the survival inventory screen.
 *
 * Redirects recipeBookPage.updateCollections() to add back collections
 * that were removed but contain only incompatible (3x3) recipes.
 * These collections were removed because getRecipes(boolean) returns
 * empty for collections where no recipes fit the 2x2 grid.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected Minecraft minecraft;

    @Shadow
    private ClientRecipeBook book;

    @Shadow
    private net.minecraft.client.gui.components.EditBox searchBox;

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void betterRecipeBook$ensureIncompatibleCollections(
            RecipeBookPage page, List<RecipeCollection> list, boolean resetPageNumber) {
        if (BetterRecipeBook.config.showAllRecipesInSurvival
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen
                && (searchBox == null || searchBox.getValue().isEmpty())) {

            for (RecipeCollection collection : this.book.getCollections()) {
                if (list.contains(collection)) continue;

                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
                if (IncompatibleCraftingUtil.hasIncompatibleRecipes(collection)) {
                    boolean hasNonIncompatible = false;
                    for (RecipeHolder<?> holder : collection.getRecipes()) {
                        if (!IncompatibleCraftingUtil.checkIncompatible(collection, holder.id())) {
                            hasNonIncompatible = true;
                            break;
                        }
                    }
                    if (!hasNonIncompatible) {
                        list.add(collection);
                    }
                }
            }
        }
        page.updateCollections(list, resetPageNumber);
    }
}
