package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;

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

    public static void markIncompatibleRecipes(RecipeCollection collection) {
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        Set<RecipeDisplayId> incompatible = null;

        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
                if (shaped.width() > 2 || shaped.height() > 2) {
                    if (incompatible == null) incompatible = new HashSet<>();
                    incompatible.add(entry.id());
                }
            }
        }

        if (incompatible != null && !incompatible.isEmpty()) {
            INCOMPATIBLE_RECIPES.put(collection, incompatible);
        } else {
            INCOMPATIBLE_RECIPES.remove(collection);
        }
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
