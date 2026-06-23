package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class IncompatibleCraftingUtil {

    // ── Stable-key cache layer ───────────────────────────────────────────
    private static final WeakHashMap<RecipeCollection, List<ResourceLocation>> KEY_CACHE = new WeakHashMap<>();
    private static final Map<List<ResourceLocation>, Set<ResourceLocation>> INCOMPATIBLE_RECIPES = new HashMap<>();
    private static final Map<List<ResourceLocation>, Integer> CHECKED_COLLECTIONS = new HashMap<>();
    private static int filteringGeneration;
    private static boolean filteringActive;

    private IncompatibleCraftingUtil() {}

    private static List<ResourceLocation> stableKey(RecipeCollection collection) {
        List<ResourceLocation> key = KEY_CACHE.get(collection);
        if (key != null) return key;

        List<RecipeHolder<?>> recipes = collection.getRecipes();
        key = new ArrayList<>(recipes.size());
        for (RecipeHolder<?> holder : recipes) {
            key.add(holder.id());
        }
        key = Collections.unmodifiableList(key);
        KEY_CACHE.put(collection, key);
        return key;
    }

    public static void clearCaches() {
        KEY_CACHE.clear();
        INCOMPATIBLE_RECIPES.clear();
        CHECKED_COLLECTIONS.clear();
        filteringGeneration = 0;
        filteringActive = false;
    }

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

    public static void markIncompatibleRecipes(RecipeCollection collection) {
        List<ResourceLocation> key = stableKey(collection);
        CHECKED_COLLECTIONS.put(key, filteringGeneration);
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
            INCOMPATIBLE_RECIPES.put(key, incompatible);
        } else {
            INCOMPATIBLE_RECIPES.remove(key);
        }
    }

    public static void markIncompatibleOnCollection(RecipeCollection collection, ResourceLocation id) {
        List<ResourceLocation> key = stableKey(collection);
        CHECKED_COLLECTIONS.put(key, filteringGeneration);
        Set<ResourceLocation> existing = INCOMPATIBLE_RECIPES.get(key);
        if (existing != null) {
            existing.add(id);
        } else {
            INCOMPATIBLE_RECIPES.put(key, new HashSet<>(Collections.singleton(id)));
        }
    }

    public static boolean isIncompatible(RecipeCollection collection, ResourceLocation id) {
        Set<ResourceLocation> set = INCOMPATIBLE_RECIPES.get(stableKey(collection));
        return set != null && set.contains(id);
    }

    public static boolean checkIncompatible(RecipeCollection collection, ResourceLocation id) {
        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (!holder.id().equals(id)) continue;
            if (holder.value() instanceof ShapedRecipe shaped)
                return shaped.getWidth() > 2 || shaped.getHeight() > 2;
            if (holder.value() instanceof ShapelessRecipe shapeless)
                return shapeless.getIngredients().size() > 4;
            return false;
        }
        return false;
    }

    public static boolean hasIncompatibleRecipes(RecipeCollection collection) {
        Set<ResourceLocation> set = INCOMPATIBLE_RECIPES.get(stableKey(collection));
        return set != null && !set.isEmpty();
    }
}
