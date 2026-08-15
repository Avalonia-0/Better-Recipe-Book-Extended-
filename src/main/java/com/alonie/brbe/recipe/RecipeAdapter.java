package com.alonie.brbe.recipe;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Adapts Minecraft recipe types to {@link DisplayRecipe}.
 *
 * <p>Each adapter is a thin wrapper that extracts the information the recipe
 * book needs (result item, search text, unique ID) without exposing the
 * underlying Minecraft type.</p>
 *
 * <p>Note: {@code fromHolder(RecipeHolder)} is not yet available in 26.2
 * because the vanilla Recipe API changed (no universal getResultItem / getDefaultInstance
 * on Recipe<?>, and RecipeHolder.id() returns ResourceKey not Identifier).
 * Use {@link #of(Identifier, ItemStack, String)} for explicit construction.</p>
 */
public final class RecipeAdapter {

    private RecipeAdapter() {}

    /** Wrap an arbitrary recipe with explicit search text. */
    public static DisplayRecipe of(Identifier id, ItemStack result, String searchString) {
        return new SimpleDisplayRecipe(id, result, searchString);
    }

    // -- Internal implementation -------------------------------------------

    private record SimpleDisplayRecipe(
            Identifier id,
            ItemStack result,
            String searchString
    ) implements DisplayRecipe {
        @Override
        public Identifier id() { return id; }

        @Override
        public ItemStack getResult() { return result.copy(); }

        @Override
        public String getSearchString() { return searchString; }
    }
}
