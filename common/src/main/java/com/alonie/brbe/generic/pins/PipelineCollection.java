package com.alonie.brbe.generic.pins;

import java.util.List;

/**
 * Seam interface that unifies vanilla {@code RecipeCollection} and
 * generic {@code GenericRecipeBookCollection} behind a common contract
 * for collection-processing pipeline stages.
 *
 * <p>Extends {@link Pinnable} so pin-sort logic is shared across both
 * collection types.  The craftability methods capture what varies
 * between vanilla (computed externally via {@code PartialCraftingUtil})
 * and generic (computed on the collection object itself).
 *
 * <p>Three pipeline stages use only this interface:
 * <ol>
 *   <li><b>Pin sort</b> — via {@link Pinnable#has}</li>
 *   <li><b>Partial sort</b> — via {@link #hasAnyCraftable} /
 *       {@link #hasAnyPartiallyCraftable}</li>
 *   <li><b>Filter toggle</b> — via same craftability methods</li>
 * </ol>
 *
 * <p>Stages that do NOT use this interface:
 * <ul>
 *   <li><b>Search</b> — result-item extraction differs fundamentally
 *       between vanilla and generic recipe types</li>
 *   <li><b>Ungroup</b> — vanilla-specific (creates new
 *       {@code RecipeCollection} instances with accessor manipulation)</li>
 * </ul>
 */
public interface PipelineCollection extends Pinnable {

    /**
     * All recipes contained in this collection, for iteration.
     */
    List<?> getRecipes();

    /**
     * Whether at least one recipe in this collection is fully craftable
     * given the current inventory.
     */
    boolean hasAnyCraftable();

    /**
     * Whether at least one recipe in this collection is partially craftable
     * (some but not all ingredients present).
     */
    boolean hasAnyPartiallyCraftable();
}
