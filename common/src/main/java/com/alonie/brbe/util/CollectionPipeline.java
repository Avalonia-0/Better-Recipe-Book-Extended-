package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, deterministic pipeline for transforming the recipe collection
 * list before it is passed to {@code RecipeBookPage.updateCollections()}.
 *
 * <p>Each stage is a pure function (or mutates in-place where documented).
 * This replaces the monolithic {@code UpdateCollectionsPipeline} with a
 * simpler design matching the 1.21.11 architectural pattern.
 */
public final class CollectionPipeline {

    private CollectionPipeline() {}

    // ═══════════════════════════════════════════════════════════════
    // Stage 1: Advanced search filter
    // ═══════════════════════════════════════════════════════════════

    /**
     * Filters collections to those whose recipe results match the advanced
     * search query.  Returns the original list unchanged when no query is
     * active or the level is unavailable.
     */
    public static List<RecipeCollection> applySearch(
            List<RecipeCollection> collections,
            SearchQuery query,
            Level level) {
        if (query == null || level == null) return collections;

        var registryAccess = level.registryAccess();
        SearchCache cache = new SearchCache();
        List<RecipeCollection> filtered = new ArrayList<>();

        for (RecipeCollection coll : collections) {
            for (RecipeHolder<?> holder : coll.getRecipes()) {
                ItemStack result = holder.value().getResultItem(registryAccess);
                if (result != null && !result.isEmpty()
                        && query.matches(result, cache)) {
                    filtered.add(coll);
                    break;
                }
            }
        }
        return filtered;
    }

    // ═══════════════════════════════════════════════════════════════
    // Stage 2: Pins sort (in-place)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Moves pinned collections to the front of the list.  Mutates the
     * list in-place — the caller's reference is updated.
     */
    public static void applyPins(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.config.enablePinning) return;
        if (collections.size() <= 1) return;

        List<RecipeCollection> snapshot = new ArrayList<>(collections);
        for (RecipeCollection coll : snapshot) {
            if (BetterRecipeBook.pinnedRecipeManager.has(
                    PinnableRecipeCollection.of(coll))) {
                collections.remove(coll);
                collections.add(0, coll);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Stage 3: Partial sort
    // ═══════════════════════════════════════════════════════════════

    /**
     * Sorts collections: truly-craftable first, then partially-craftable,
     * then uncraftable.  Returns a new list with the sorted order.
     *
     * @param useFullSort   three-bucket sort (true) vs two-bucket (false)
     * @param hasPartialData whether partial-material data is available
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

    // ═══════════════════════════════════════════════════════════════
    // Stage 4: Filter toggle
    // ═══════════════════════════════════════════════════════════════

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
