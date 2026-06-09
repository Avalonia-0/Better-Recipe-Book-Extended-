package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Modifies the return value of RecipeCollection.getRecipes(boolean) to include
 * incompatible (3x3) recipes when showAllRecipesInSurvival is enabled on the
 * inventory screen.
 *
 * This ensures:
 * 1. RecipeButton.init() gets a non-empty recipe list → no div-by-zero crash
 * 2. Search filter finds incompatible recipes matching the query
 * 3. getOrderedRecipes() returns incompatible recipes via getRecipes(bool)
 */
@Mixin(RecipeCollection.class)
public abstract class RecipeCollectionMixin {

    @ModifyVariable(method = "getRecipes", at = @At("RETURN"), ordinal = 0)
    private List<RecipeHolder<?>> betterRecipeBook$includeIncompatibleRecipes(
            List<RecipeHolder<?>> recipes) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return recipes;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return recipes;
        if (recipes == null) return recipes;

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
            return combined;
        }
        return recipes;
    }
}
