package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensures incompatible (3×3) recipes are included in getRecipes(boolean)
 * return value so buttons have a non-empty recipe list.
 *
 * In 1.21.1, RecipeButton.init() calls collection.getRecipes(isFiltering)
 * NOT getDisplayRecipes(). When isFiltering=false, getRecipes returns only
 * recipes that fit the grid (fitsDimensions set). 3×3 recipes don't fit
 * the 2×2 inventory grid, so the list would be empty → crash in
 * renderWidget (currentIndex % 0).
 *
 * When isFiltering=true (craftable-only), we skip adding incompatible
 * recipes since they are not craftable.
 */
@Mixin(RecipeCollection.class)
public abstract class RecipeCollectionMixin {

    @Inject(method = "getRecipes(boolean)", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$includeIncompatibleRecipes(
            boolean craftableOnly, CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;
        // When filtering by craftable-only, don't add incompatible recipes
        if (craftableOnly) return;

        List<RecipeHolder<?>> recipes = cir.getReturnValue();
        if (recipes == null) return;

        RecipeCollection self = (RecipeCollection) (Object) this;

        List<RecipeHolder<?>> extras = null;
        for (RecipeHolder<?> holder : self.getRecipes()) {
            if (IncompatibleCraftingUtil.checkIncompatible(self, holder.id())
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
