package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic pipeline for transforming the recipe collection list
 * before it is passed to {@code RecipeBookPage.updateCollections()}.
 *
 * <h3>Pipeline order (this is the single source of truth)</h3>
 * <ol>
 *   <li><b>Search filter</b> — remove collections that don't match an
 *       advanced search query.  Runs first to reduce the working set.</li>
 *   <li><b>Ungroup split</b> — split multi-recipe collections into
 *       single-recipe collections (when {@code noGrouped} is on).</li>
 *   <li><b>Pins sort</b> — move pinned collections to the front of the
 *       list.  Runs after ungroup so it sees the final collection objects.</li>
 *   <li><b>Partial sort</b> — sort craftable before partially-craftable
 *       before uncraftable.</li>
 * </ol>
 *
 * <p>Each stage is a pure function (or mutates in-place where documented),
 * making the pipeline testable without the Mixin framework.
 */
public final class CollectionPipeline {

    private CollectionPipeline() {}

    // ---- Stage 1: Advanced search filter ----

    /**
     * Filters the list to only collections whose result items match
     * the advanced search query.  Returns a new list.
     */
    public static List<RecipeCollection> applySearch(
            List<RecipeCollection> collections,
            SearchQuery query,
            ContextMap displayContext) {
        if (query == null || displayContext == null) {
            return collections;
        }

        SearchCache cache = new SearchCache();
        List<RecipeCollection> filtered = new ArrayList<>();

        for (RecipeCollection collection : collections) {
            boolean added = false;
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                List<ItemStack> results = entry.resultItems(displayContext);
                for (ItemStack result : results) {
                    if (result != null && !result.isEmpty()
                            && query.matches(result, cache)) {
                        filtered.add(collection);
                        added = true;
                        break;
                    }
                }
                if (added) break;
            }
        }

        return filtered;
    }

    // ---- Stage 2: Ungroup split ----

    /**
     * Splits multi-recipe collections into single-recipe collections when
     * {@code alternativeRecipes.noGrouped} is enabled.  Returns a new list.
     */
    public static List<RecipeCollection> applyUngroup(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.config.alternativeRecipes.noGrouped) {
            return collections;
        }

        List<RecipeCollection> split = new ArrayList<>(collections.size());
        for (RecipeCollection collection : collections) {
            List<RecipeDisplayEntry> recipes = collection.getRecipes();
            if (recipes.size() <= 1) {
                split.add(collection);
                continue;
            }

            RecipeCollectionAccessor source = (RecipeCollectionAccessor) collection;
            boolean restrictToCraftableOrPartial = PartialCraftingUtil.hasPartialMaterials(collection)
                    || collection.hasCraftable();
            boolean addedAny = false;

            for (RecipeDisplayEntry recipe : recipes) {
                if (!source.brbe$getSelected().contains(recipe.id())) {
                    continue;
                }

                boolean isCraftable = source.brbe$getCraftable().contains(recipe.id());
                boolean isPartial = PartialCraftingUtil.isPartiallyCraftable(collection, recipe.id());
                if (restrictToCraftableOrPartial && !isCraftable && !isPartial) {
                    continue;
                }

                RecipeCollection child = new RecipeCollection(Collections.singletonList(recipe));
                RecipeCollectionAccessor childAccessor = (RecipeCollectionAccessor) child;
                childAccessor.brbe$getSelected().add(recipe.id());
                if (isCraftable) {
                    childAccessor.brbe$getCraftable().add(recipe.id());
                }
                if (isPartial) {
                    PartialCraftingUtil.markPartialMaterial(child, recipe.id());
                }

                split.add(child);
                addedAny = true;
            }

            if (!addedAny && !restrictToCraftableOrPartial) {
                split.add(collection);
            }
        }

        return split;
    }

    // ---- Stage 3: Pins sort (in-place) ----

    /**
     * Moves pinned collections to the front of the list.  Mutates the list
     * in place (removes and re-inserts at index 0).
     */
    public static void applyPins(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.config.enablePinning) {
            return;
        }

        // Iterate a snapshot to avoid ConcurrentModificationException
        List<RecipeCollection> snapshot = new ArrayList<>(collections);
        for (RecipeCollection collection : snapshot) {
            if (BetterRecipeBook.pinnedRecipeManager.has(PinnableRecipeCollection.of(collection))) {
                collections.remove(collection);
                collections.add(0, collection);
            }
        }
    }

    // ---- Stage 4: Partial sort ----

    /**
     * Sorts collections: truly-craftable first, then partial, then uncraftable.
     * When {@code useFullSort} is false, uses a simpler 2-group sort
     * (any-craftable-or-partial before rest).
     */
    public static List<RecipeCollection> applyPartialSort(
            List<RecipeCollection> collections,
            boolean useFullSort,
            boolean hasPartialData) {

        List<RecipeCollection> front = new ArrayList<>();
        List<RecipeCollection> middle = new ArrayList<>();
        List<RecipeCollection> back = new ArrayList<>();

        for (RecipeCollection c : collections) {
            if (hasPartialData) {
                CollectionCategory cat = PartialCraftingUtil.categorize(c);
                if (useFullSort) {
                    switch (cat) {
                        case TRULY_CRAFTABLE -> front.add(c);
                        case PARTIAL -> middle.add(c);
                        case UNASSIGNED -> back.add(c);
                    }
                } else {
                    if (cat != CollectionCategory.UNASSIGNED) front.add(c);
                    else back.add(c);
                }
            } else {
                if (c.hasCraftable()) front.add(c);
                else back.add(c);
            }
        }

        List<RecipeCollection> result = new ArrayList<>(collections.size());
        result.addAll(front);
        result.addAll(middle);
        result.addAll(back);
        return result;
    }

    // ---- Stage 5: Filter toggle ----

    /**
     * Removes collections that have no craftable (and, when partial marking
     * is enabled, no partially-craftable) recipes.  Returns a new list.
     * When the filter toggle is off, returns the original list unchanged.
     */
    public static List<RecipeCollection> applyFilterToggle(
            List<RecipeCollection> collections,
            boolean isFiltering) {
        if (!isFiltering) return collections;

        boolean hasPartial = BetterRecipeBook.config.partialMarkingEnabled;
        List<RecipeCollection> result = new ArrayList<>();
        for (RecipeCollection coll : collections) {
            boolean keep = hasPartial
                    ? coll.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(coll)
                    : coll.hasCraftable();
            if (keep) result.add(coll);
        }
        return result;
    }
}
