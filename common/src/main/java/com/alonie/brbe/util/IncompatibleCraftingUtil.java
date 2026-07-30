package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.*;

/**
 * Detects recipes that require a larger crafting grid than is currently
 * available (e.g. 3×3 recipes on a 2×2 inventory screen).
 *
 * <p>Data is stored via {@link RecipeCollectionTagger} for generation-aware
 * lifecycle management, replacing the previous inline WeakHashMap pattern.</p>
 */
public final class IncompatibleCraftingUtil {

    private static final RecipeCollectionTagger<ResourceLocation> tagger =
            new RecipeCollectionTagger<>();

    private IncompatibleCraftingUtil() {}

    // ── Lifecycle ────────────────────────────────────────────────────

    public static boolean isActive() {
        // Active whenever the tagger has been started for this cycle.
        // In practice checked via the mixin's retainIncompatible flag.
        return true;
    }

    public static void beginFiltering(boolean active) {
        tagger.beginFiltering(active);
    }

    public static void clearCaches() {
        // no-op: WeakHashMap-style storage auto-cleans via the tagger.
        // Kept for callers that expect an explicit cleanup hook.
    }

    // ── Marking ──────────────────────────────────────────────────────

    /**
     * Marks recipes in the collection that require a larger grid than
     * the current 2×2 inventory screen.  Also populates
     * {@code fitsDimensions} so the pipeline can display them.
     *
     * <p><b>Note:</b> the fitsDimensions write is a legacy coupling that
     * will be moved to the unified pipeline in Phase 2 of the refactor.</p>
     */
    public static void markIncompatibleRecipes(RecipeCollection collection) {
        tagger.markAsChecked(collection);
        Set<ResourceLocation> incompatible = null;
        RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;

        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (isLargeRecipe(holder.value())) {
                if (incompatible == null) incompatible = new HashSet<>();
                incompatible.add(holder.id());
                // Legacy: populate fitsDimensions so the pipeline displays
                // these recipes.  Will move to the pipeline in Phase 2.
                accessor.getFitsDimensions().add(holder);
            }
        }

        if (incompatible != null && !incompatible.isEmpty()) {
            tagger.setAllTags(collection, incompatible);
        } else {
            tagger.clearTags(collection);
        }
    }

    public static void markIncompatibleOnCollection(RecipeCollection collection, ResourceLocation id) {
        tagger.markAsChecked(collection);
        tagger.addTag(collection, id);
    }

    // ── Queries ──────────────────────────────────────────────────────

    public static boolean isIncompatible(RecipeCollection collection, ResourceLocation id) {
        return tagger.hasTagEvenIfStale(collection, id);
    }

    /**
     * Checks whether a specific recipe within a collection is incompatible
     * by scanning the collection's recipes for a large-grid match.
     */
    public static boolean checkIncompatible(RecipeCollection collection, ResourceLocation id) {
        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (!holder.id().equals(id)) continue;
            return isLargeRecipe(holder.value());
        }
        return false;
    }

    /**
     * Collection-free check — works even when the recipe is not in any
     * vanilla RecipeCollection (e.g. RBIP creative-mode tabs).
     */
    public static boolean checkIncompatible(RecipeHolder<?> holder) {
        return holder != null && isLargeRecipe(holder.value());
    }

    public static boolean hasIncompatibleRecipes(RecipeCollection collection) {
        return tagger.hasAnyTagEvenIfStale(collection);
    }

    // ── Internal ─────────────────────────────────────────────────────

    private static boolean isLargeRecipe(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped)
            return shaped.getWidth() > 2 || shaped.getHeight() > 2;
        if (recipe instanceof ShapelessRecipe shapeless)
            return shapeless.getIngredients().size() > 4;
        return false;
    }
}
