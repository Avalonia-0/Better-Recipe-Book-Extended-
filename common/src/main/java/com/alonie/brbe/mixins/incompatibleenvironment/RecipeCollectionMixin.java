package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensures incompatible (3x3) recipes are included in RecipeButton's
 * ordered recipe list. Intercepts getOrderedRecipes() at RETURN.
 *
 * In 1.21.1, RecipeButton.getOrderedRecipes() calls
 * collection.getRecipes(book.isFiltering(menu)) which returns recipes
 * only from the fitsDimensions set (when not filtering). 3x3 recipes
 * don't fit the 2x2 grid, so they're absent → empty list → crash in
 * renderWidget (currentIndex % 0).
 *
 * When filtering by craftable-only, we skip adding incompatible recipes.
 */
@Mixin(RecipeButton.class)
public abstract class RecipeCollectionMixin {

    @Shadow private RecipeCollection collection;

    @Inject(method = "getOrderedRecipes", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$includeIncompatibleRecipes(
            CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<RecipeHolder<?>> recipes = cir.getReturnValue();
        if (recipes == null) return;

        // Add incompatible (3x3) recipes not already in the list
        List<RecipeHolder<?>> extras = null;
        for (RecipeHolder<?> holder : this.collection.getRecipes()) {
            if (IncompatibleCraftingUtil.checkIncompatible(this.collection, holder.id())
                    && !recipes.contains(holder)) {
                if (extras == null) extras = new ArrayList<>();
                extras.add(holder);
            }
        }

        if (extras != null) {
            List<RecipeHolder<?>> combined = new ArrayList<>(recipes);
            combined.addAll(extras);
            cir.setReturnValue(combined);
        }
    }
}
