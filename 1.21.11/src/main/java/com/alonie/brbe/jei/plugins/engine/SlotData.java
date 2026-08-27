package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A slot declared by a plugin's {@code setRecipe}: its lookup role, its layout
 * position ({@code x}/{@code y} relative to the recipe layout, {@code -1} when
 * the slot is invisible — added via {@code addInvisibleIngredients}) and the
 * concrete {@link ItemStack}s added to it.  This is the data-only analogue of
 * JEI's {@code SlotIngredient}, stripped of renderer / typed-ingredient
 * machinery.
 */
public record SlotData(RecipeIngredientRole role, int x, int y, List<ItemStack> stacks) {
    /** Whether this slot has a visible layout position (vs. invisible). */
    public boolean visible() {
        return x >= 0 && y >= 0;
    }
}
