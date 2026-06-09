package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

public final class IncompatibleCraftingUtil {
    private static final WeakHashMap<RecipeCollection, Set<ResourceLocation>> INCOMPATIBLE_RECIPES = new WeakHashMap<>();
    private static final WeakHashMap<RecipeCollection, Integer> CHECKED_COLLECTIONS = new WeakHashMap<>();
    private static int filteringGeneration;
    private static boolean filteringActive;

    private IncompatibleCraftingUtil() {}

    public static boolean isActive() {
        return filteringActive;
    }

    public static void beginFiltering(boolean active) {
        filteringActive = active;
        if (active) filteringGeneration++;
    }

    public static void markIncompatibleRecipes(RecipeCollection collection) {
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        Set<ResourceLocation> incompatible = null;

        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (holder.value() instanceof ShapedRecipe shaped) {
                if (shaped.getWidth() > 2 || shaped.getHeight() > 2) {
                    if (incompatible == null) incompatible = new HashSet<>();
                    incompatible.add(holder.id());
                }
            } else if (holder.value() instanceof ShapelessRecipe shapeless) {
                if (shapeless.getIngredients().size() > 4) {
                    if (incompatible == null) incompatible = new HashSet<>();
                    incompatible.add(holder.id());
                }
            }
        }

        if (incompatible != null && !incompatible.isEmpty()) {
            INCOMPATIBLE_RECIPES.put(collection, incompatible);
        } else {
            INCOMPATIBLE_RECIPES.remove(collection);
        }
    }

    public static boolean isIncompatible(RecipeCollection collection, ResourceLocation id) {
        Set<ResourceLocation> set = INCOMPATIBLE_RECIPES.get(collection);
        return set != null && set.contains(id);
    }

    public static boolean hasIncompatibleRecipes(RecipeCollection collection) {
        Set<ResourceLocation> set = INCOMPATIBLE_RECIPES.get(collection);
        return set != null && !set.isEmpty();
    }
}
