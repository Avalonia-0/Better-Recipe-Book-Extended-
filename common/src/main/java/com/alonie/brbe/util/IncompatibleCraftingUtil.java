package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.*;

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
        if (active) {
            if (filteringGeneration == Integer.MAX_VALUE) {
                INCOMPATIBLE_RECIPES.clear();
                CHECKED_COLLECTIONS.clear();
                filteringGeneration = 0;
            }
            filteringGeneration++;
        }
    }

    /**
     * Compatibility shim — WeakHashMaps auto-clean, so explicit clear
     * is not needed, but we keep it for callers.
     */
    public static void clearCaches() {
        // no-op: WeakHashMaps handle cleanup automatically
    }

    public static void markIncompatibleRecipes(RecipeCollection collection) {
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        Set<ResourceLocation> incompatible = null;
        RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;

        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (holder.value() instanceof ShapedRecipe shaped) {
                if (shaped.getWidth() > 2 || shaped.getHeight() > 2) {
                    if (incompatible == null) incompatible = new HashSet<>();
                    incompatible.add(holder.id());
                    accessor.getFitsDimensions().add(holder);
                }
            } else if (holder.value() instanceof ShapelessRecipe shapeless) {
                if (shapeless.getIngredients().size() > 4) {
                    if (incompatible == null) incompatible = new HashSet<>();
                    incompatible.add(holder.id());
                    accessor.getFitsDimensions().add(holder);
                }
            }
        }

        if (incompatible != null && !incompatible.isEmpty()) {
            INCOMPATIBLE_RECIPES.put(collection, incompatible);
        } else {
            INCOMPATIBLE_RECIPES.remove(collection);
        }
    }

    public static void markIncompatibleOnCollection(RecipeCollection collection, ResourceLocation id) {
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        Set<ResourceLocation> existing = INCOMPATIBLE_RECIPES.get(collection);
        if (existing != null) {
            existing.add(id);
        } else {
            INCOMPATIBLE_RECIPES.put(collection, new HashSet<>(Collections.singleton(id)));
        }
    }

    public static boolean isIncompatible(RecipeCollection collection, ResourceLocation id) {
        Set<ResourceLocation> set = INCOMPATIBLE_RECIPES.get(collection);
        return set != null && set.contains(id);
    }

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

    private static boolean isLargeRecipe(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped)
            return shaped.getWidth() > 2 || shaped.getHeight() > 2;
        if (recipe instanceof ShapelessRecipe shapeless)
            return shapeless.getIngredients().size() > 4;
        return false;
    }

    public static boolean hasIncompatibleRecipes(RecipeCollection collection) {
        Set<ResourceLocation> set = INCOMPATIBLE_RECIPES.get(collection);
        return set != null && !set.isEmpty();
    }
}
