package com.alonie.brbe.recipeviewer;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Registry of viewer categories.  Add new categories (furnace, smithing, …)
 * here and they automatically appear as bottom tabs on the overlay.
 */
public final class RecipeViewerCategories {

    private RecipeViewerCategories() {}

    public static final List<RecipeViewerCategory> REGISTRY =
            List.of(new FurnaceRecipeCategory(), new CraftingRecipeCategory());

    /**
     * Pick the default category for {@code target} on open: the applicable
     * category with the highest {@link RecipeViewerCategory#defaultPriority}
     * whose query yields at least one entry.  Returns null when no category can
     * show anything for {@code target} (the viewer does not open).
     */
    public static RecipeViewerCategory defaultFor(ItemStack target, boolean usage) {
        RecipeViewerCategory best = null;
        int bestPriority = -1;
        for (RecipeViewerCategory category : REGISTRY) {
            int priority = category.defaultPriority(target);
            if (priority <= bestPriority) continue;
            if (!category.query(target, usage).isEmpty()) {
                best = category;
                bestPriority = priority;
            }
        }
        return best;
    }
}
