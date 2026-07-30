package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.generic.pins.PipelineCollection;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
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
    // Stage 2: Ungroup split
    // ═══════════════════════════════════════════════════════════════

    /**
     * Splits multi-recipe collections into single-recipe collections when
     * {@code alternativeRecipes.noGrouped} is enabled.  Returns a new list.
     */
    public static List<RecipeCollection> applyUngroup(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.ctx().config().alternativeRecipes.noGrouped) {
            return collections;
        }

        List<RecipeCollection> split = new ArrayList<>(collections.size());
        for (RecipeCollection collection : collections) {
            List<RecipeHolder<?>> recipes = collection.getRecipes();
            if (recipes.size() <= 1) {
                split.add(collection);
                continue;
            }

            var source = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) collection;
            boolean restrictToCraftableOrPartial = PartialCraftingUtil.hasPartialMaterials(collection)
                    || collection.hasCraftable();
            boolean addedAny = false;

            for (RecipeHolder<?> recipe : recipes) {
                // Only split recipes that fit dimensions
                boolean fits = false;
                for (var h : source.getFitsDimensions()) {
                    if (h.id().equals(recipe.id())) { fits = true; break; }
                }
                if (!fits) continue;

                boolean isCraftable = false;
                for (var h : source.brbe$getCraftable()) {
                    if (h.id().equals(recipe.id())) { isCraftable = true; break; }
                }
                boolean isPartial = PartialCraftingUtil.isPartiallyCraftable(collection, recipe.id());
                if (restrictToCraftableOrPartial && !isCraftable && !isPartial) {
                    continue;
                }

                RecipeCollection child = new RecipeCollection(
                        collection.registryAccess(),
                        Collections.singletonList(recipe));
                if (isCraftable) {
                    var ca = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) child;
                    ca.brbe$getCraftable().add(recipe);
                }
                if (isPartial) {
                    PartialCraftingUtil.markPartialMaterial(child, recipe.id());
                }
                // Populate fitsDimensions so the child collection displays properly.
                // Without this, the child appears as an empty group because vanilla
                // checks fitsDimensions to determine which recipes are visible.
                {
                    var childAcc = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) child;
                    childAcc.getFitsDimensions().add(recipe);
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

    // ═══════════════════════════════════════════════════════════════
    // Stage 3: Pins sort (in-place — was Stage 2 before ungroup added)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Moves pinned collections to the front of the list.  Mutates the
     * list in-place — the caller's reference is updated.
     */
    public static void applyPins(List<RecipeCollection> collections) {
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
    // Stage 4: Partial sort (pin-aware)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Sorts collections with pinned recipes always at highest priority,
     * then fully-craftable, then partially-craftable, then uncraftable.
     *
     * <p>Pin priority is absolute: a pinned uncraftable recipe comes
     * before an unpinned craftable one.  Within each pin group, the
     * standard category ordering applies.
     *
     * @param hasPartialData whether partial-material data is available
     *                       (controls whether partial recipes get a
     *                       dedicated middle bucket vs grouped with
     *                       uncraftable)
     */
    public static List<RecipeCollection> applyPartialSort(
            List<RecipeCollection> collections,
            boolean hasPartialData) {

        // Phase 1: split by pin status
        List<RecipeCollection> pinnedCraftable = new ArrayList<>();
        List<RecipeCollection> pinnedPartial = new ArrayList<>();
        List<RecipeCollection> pinnedUncraftable = new ArrayList<>();
        List<RecipeCollection> unpinnedCraftable = new ArrayList<>();
        List<RecipeCollection> unpinnedPartial = new ArrayList<>();
        List<RecipeCollection> unpinnedUncraftable = new ArrayList<>();

        for (RecipeCollection c : collections) {
            boolean isPinned = BetterRecipeBook.pinnedRecipeManager.has(
                        PinnableRecipeCollection.of(c));

            if (hasPartialData) {
                // Use EvenIfStale to prevent category flicker when sorting
                // runs across a generation boundary (tab switch, config change).
                CollectionCategory cat = PartialCraftingUtil.categorizeEvenIfStale(c);
                if (isPinned) {
                    switch (cat) {
                        case TRULY_CRAFTABLE -> pinnedCraftable.add(c);
                        case PARTIAL -> pinnedPartial.add(c);
                        case UNASSIGNED -> pinnedUncraftable.add(c);
                    }
                } else {
                    switch (cat) {
                        case TRULY_CRAFTABLE -> unpinnedCraftable.add(c);
                        case PARTIAL -> unpinnedPartial.add(c);
                        case UNASSIGNED -> unpinnedUncraftable.add(c);
                    }
                }
            } else {
                if (c.hasCraftable()) {
                    if (isPinned) pinnedCraftable.add(c);
                    else unpinnedCraftable.add(c);
                } else {
                    if (isPinned) pinnedUncraftable.add(c);
                    else unpinnedUncraftable.add(c);
                }
            }
        }

        // Phase 2: assemble — pinned before unpinned in each category
        List<RecipeCollection> result = new ArrayList<>(collections.size());
        result.addAll(pinnedCraftable);
        result.addAll(pinnedPartial);
        result.addAll(pinnedUncraftable);
        result.addAll(unpinnedCraftable);
        result.addAll(unpinnedPartial);
        result.addAll(unpinnedUncraftable);
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

        boolean hasPartial = BetterRecipeBook.ctx().config().partialMarkingEnabled;
        List<RecipeCollection> result = new ArrayList<>();
        for (RecipeCollection coll : collections) {
            boolean keep = hasPartial
                    ? coll.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(coll)
                    : coll.hasCraftable();
            if (keep) result.add(coll);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Generic overloads (PipelineCollection)
    // These work with both vanilla RecipeCollection (via
    // VanillaPipelineCollection adapter) and GenericRecipeBookCollection.
    // ═══════════════════════════════════════════════════════════════

    /**
     * Moves pinned collections to the front of the list.  Mutates the
     * list in-place.  Works with any {@link PipelineCollection}.
     */
    public static <T extends PipelineCollection> void applyPinsGeneric(List<T> collections) {
        if (collections.size() <= 1) return;

        List<T> snapshot = new ArrayList<>(collections);
        for (T coll : snapshot) {
            if (BetterRecipeBook.pinnedRecipeManager.has(coll)) {
                collections.remove(coll);
                collections.add(0, coll);
            }
        }
    }

    /**
     * Sorts collections with pinned recipes always at highest priority,
     * then fully-craftable, then partially-craftable, then uncraftable.
     *
     * <p>Pin priority is absolute: a pinned uncraftable recipe comes
     * before an unpinned craftable one.  Works with any
     * {@link PipelineCollection}.
     */
    public static <T extends PipelineCollection> List<T> applyPartialSortGeneric(
            List<T> collections) {

        List<T> pinnedCraftable = new ArrayList<>();
        List<T> pinnedPartial = new ArrayList<>();
        List<T> pinnedUncraftable = new ArrayList<>();
        List<T> unpinnedCraftable = new ArrayList<>();
        List<T> unpinnedPartial = new ArrayList<>();
        List<T> unpinnedUncraftable = new ArrayList<>();

        for (T c : collections) {
            boolean isPinned = BetterRecipeBook.pinnedRecipeManager.has(c);

            boolean craftable = c.hasAnyCraftable();
            boolean partial = c.hasAnyPartiallyCraftable();

            if (isPinned) {
                if (craftable) pinnedCraftable.add(c);
                else if (partial) pinnedPartial.add(c);
                else pinnedUncraftable.add(c);
            } else {
                if (craftable) unpinnedCraftable.add(c);
                else if (partial) unpinnedPartial.add(c);
                else unpinnedUncraftable.add(c);
            }
        }

        List<T> result = new ArrayList<>(collections.size());
        result.addAll(pinnedCraftable);
        result.addAll(pinnedPartial);
        result.addAll(pinnedUncraftable);
        result.addAll(unpinnedCraftable);
        result.addAll(unpinnedPartial);
        result.addAll(unpinnedUncraftable);
        return result;
    }

    /**
     * Removes collections that have no craftable (and, when partial marking
     * is enabled, no partially-craftable) recipes.  Returns a new list.
     * When the filter toggle is off, returns the original list unchanged.
     * Works with any {@link PipelineCollection}.
     */
    public static <T extends PipelineCollection> List<T> applyFilterToggleGeneric(
            List<T> collections,
            boolean isFiltering) {
        if (!isFiltering) return collections;

        boolean hasPartial = BetterRecipeBook.ctx().config().partialMarkingEnabled;
        List<T> result = new ArrayList<>();
        for (T coll : collections) {
            boolean keep = hasPartial
                    ? coll.hasAnyCraftable() || coll.hasAnyPartiallyCraftable()
                    : coll.hasAnyCraftable();
            if (keep) result.add(coll);
        }
        return result;
    }
}
