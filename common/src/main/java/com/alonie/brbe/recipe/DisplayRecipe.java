package com.alonie.brbe.recipe;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Lightweight recipe interface for display in the recipe book.
 *
 * <p>Replaces the old {@code GenericRecipe} with a smaller, more focused
 * contract.  Implementations are thin wrappers around Minecraft recipe types
 * ({@code RecipeHolder}, {@code SmithingRecipe}, potion brewing mixes).</p>
 */
public interface DisplayRecipe {

    /** Unique identifier for this recipe (used for pinning). */
    Identifier id();

    /** The result item shown on the recipe button. */
    ItemStack getResult();

    /**
     * Text used for search matching.  Typically the hover name of the result
     * item, but may include additional terms for specialised recipes (e.g.
     * potion effect names for brewing recipes).
     */
    String getSearchString();

    /**
     * Whether this recipe ID matches the given one.  Used by the pinning system.
     */
    default boolean matches(Identifier other) {
        return id().equals(other);
    }
}
