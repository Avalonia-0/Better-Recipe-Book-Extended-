package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks which recipes in a RecipeCollection require a 3×3 crafting grid
 * and are therefore incompatible with the 2×2 inventory crafting grid.
 * <p>
 * Follows the same {@link WeakHashMap} pattern as {@link PartialCraftingUtil}.
 */
public final class IncompatibleCraftingUtil {
    private static final WeakHashMap<RecipeCollection, Set<RecipeDisplayId>> INCOMPATIBLE_RECIPES = new WeakHashMap<>();
    private static final WeakHashMap<RecipeCollection, Integer> CHECKED_COLLECTIONS = new WeakHashMap<>();
    private static int filteringGeneration;
    private static boolean filteringActive;

    private IncompatibleCraftingUtil() {}

    public static boolean isActive() {
        return filteringActive;
    }

    public static void beginFiltering(boolean active) {
        filteringActive = active;
        if (active) {
            filteringGeneration++;
        }
    }

    /**
     * Scans a RecipeCollection for recipes that need a 3×3 grid
     * ({@code ShapedCraftingRecipeDisplay} with width &gt; 2 or height &gt; 2).
     */
    public static void markIncompatibleRecipes(RecipeCollection collection) {
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        Set<RecipeDisplayId> incompatible = null;

        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
                if (shaped.width() > 2 || shaped.height() > 2) {
                    if (incompatible == null) incompatible = new HashSet<>();
                    incompatible.add(entry.id());
                }
            } else if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
                if (shapeless.ingredients().size() > 4) {
                    if (incompatible == null) incompatible = new HashSet<>();
                    incompatible.add(entry.id());
                }
            } else {
                // Unknown display type — canDisplay returns false for these on any
                // grid size (vanilla behaviour), so they are inherently incompatible.
                if (incompatible == null) incompatible = new HashSet<>();
                incompatible.add(entry.id());
            } else {
                if (incompatible == null) incompatible = new HashSet<>();
                incompatible.add(entry.id());
            } else {
                if (incompatible == null) incompatible = new HashSet<>();
                incompatible.add(entry.id());
            }
        }

        if (incompatible != null && !incompatible.isEmpty()) {
            INCOMPATIBLE_RECIPES.put(collection, incompatible);
        } else {
            INCOMPATIBLE_RECIPES.remove(collection);
        }
    }

    /** Call when a collection is split (ungroup) to preserve the incompatible mark. */
    public static void markIncompatibleOnCollection(RecipeCollection collection, RecipeDisplayId id) {
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        INCOMPATIBLE_RECIPES.put(collection, new HashSet<>(Collections.singleton(id)));
    }

    public static boolean wasChecked(RecipeCollection collection) {
        Integer generation = CHECKED_COLLECTIONS.get(collection);
        return filteringActive && generation != null && generation == filteringGeneration;
    }

    public static boolean isIncompatible(RecipeCollection collection, RecipeDisplayId id) {
        Set<RecipeDisplayId> set = INCOMPATIBLE_RECIPES.get(collection);
        return set != null && set.contains(id);
    }

    public static boolean hasIncompatibleRecipes(RecipeCollection collection) {
        Set<RecipeDisplayId> set = INCOMPATIBLE_RECIPES.get(collection);
        return set != null && !set.isEmpty();
    }
}
