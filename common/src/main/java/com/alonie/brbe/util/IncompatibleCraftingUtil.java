package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

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
     * The single source of truth for the "incompatible" tag.
     * A recipe is incompatible if it's shaped and requires a grid larger than 2×2.
     */
    public static boolean isIncompatibleDisplay(RecipeDisplay display) {
        return display instanceof ShapedCraftingRecipeDisplay shaped
                && (shaped.width() > 2 || shaped.height() > 2);
    }

    public static void markIncompatibleRecipes(RecipeCollection collection) {
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        Set<RecipeDisplayId> incompatible = null;

        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (isIncompatibleDisplay(entry.display())) {
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

    public static void markIncompatibleOnCollection(RecipeCollection collection, RecipeDisplayId id) {
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        INCOMPATIBLE_RECIPES.put(collection, new HashSet<>(Collections.singleton(id)));
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
