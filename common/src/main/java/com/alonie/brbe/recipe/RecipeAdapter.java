package com.alonie.brbe.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Adapts Minecraft recipe types to {@link DisplayRecipe}.
 *
 * <p>Each adapter is a thin wrapper that extracts the information the recipe
 * book needs (result item, search text, unique ID) without exposing the
 * underlying Minecraft type.</p>
 */
public final class RecipeAdapter {

    private RecipeAdapter() {}

    /** Wrap a vanilla {@link RecipeHolder} as a DisplayRecipe. */
    public static DisplayRecipe fromHolder(RecipeHolder<?> holder) {
        ItemStack result = holder.value().getResultItem(null);
        return new SimpleDisplayRecipe(
                holder.id(),
                result,
                result.getHoverName().getString()
        );
    }

    /** Wrap an arbitrary recipe with explicit search text. */
    public static DisplayRecipe of(ResourceLocation id, ItemStack result, String searchString) {
        return new SimpleDisplayRecipe(id, result, searchString);
    }

    // -- Internal implementation -------------------------------------------

    private record SimpleDisplayRecipe(
            ResourceLocation id,
            ItemStack result,
            String searchString
    ) implements DisplayRecipe {
        @Override
        public ResourceLocation id() { return id; }

        @Override
        public ItemStack getResult() { return result.copy(); }

        @Override
        public String getSearchString() { return searchString; }
    }
}
