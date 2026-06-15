package com.alonie.brbe.util;

/**
 * Classification of a {@code RecipeCollection} based on the craftability
 * of its individual recipes.
 *
 * <p>Used by sort logic to decide page placement order, and by
 * {@code RecipeButtonMixin} to filter cycling lists.</p>
 *
 * <p>The classification iterates every recipe in the collection:
 * a recipe that is {@link PartialCraftingUtil#isPartiallyCraftable
 * isPartiallyCraftable} counts as {@link #PARTIAL}; a recipe that is
 * craftable but NOT partial counts as truly-craftable.  The collection
 * as a whole takes the highest-priority category present.</p>
 */
public enum CollectionCategory {

    /** At least one recipe is fully craftable (and not partial-injected). */
    TRULY_CRAFTABLE,

    /** Has partial-injected recipes but no truly-craftable ones. */
    PARTIAL,

    /** Neither craftable nor partial-injected. */
    UNASSIGNED
}
