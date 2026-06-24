package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class PartialCraftingUtil {

    // ── Stable-key cache layer ───────────────────────────────────────────
    // Key memoization: maps RecipeCollection (object identity) → stable key.
    // WeakHashMap so keys auto-clean when RecipeCollections are GC'd.
    private static final WeakHashMap<RecipeCollection, List<ResourceLocation>> KEY_CACHE = new WeakHashMap<>();

    // Data caches: use stable keys (recipe ID lists) instead of object
    // identity.  Survives RecipeCollection recreation by external mods.
    private static final Map<List<ResourceLocation>, Set<ResourceLocation>> PARTIAL_RECIPES = new HashMap<>();
    private static final Map<List<ResourceLocation>, Integer> CHECKED_COLLECTIONS = new HashMap<>();
    // Category cache: avoids re-categorizing non-dirty collections during
    // incremental sort passes (saves ~46ms per call on ATM10-size modpacks).
    private static final Map<List<ResourceLocation>, CollectionCategory> CATEGORY_CACHE = new HashMap<>();

    private static int filteringGeneration;
    private static boolean filteringActive;

    private PartialCraftingUtil() {}

    /**
     * Builds a stable, value-based key from a RecipeCollection's recipe IDs.
     * The key is memoized per RecipeCollection instance for performance;
     * if the RecipeCollection is recreated (e.g. by an external mod), the key
     * is recomputed from the new object but still matches the old key because
     * recipe IDs don't change.
     */
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

    /**
     * Clears all cached state.  Call when the recipe manager reloads
     * (datapack reload, server sync) to prevent stale entries.
     */
    public static void clearCaches() {
        KEY_CACHE.clear();
        PARTIAL_RECIPES.clear();
        CHECKED_COLLECTIONS.clear();
        CATEGORY_CACHE.clear();
        filteringGeneration = 0;
        filteringActive = false;
    }

    /** Clear only the category cache — called when forEach/partialMark
     *  have re-evaluated craftability, invalidating categorization. */
    public static void clearCategoryCache() {
        CATEGORY_CACHE.clear();
    }

    /** Invalidate the category cache for a specific collection (e.g. because
     *  its craftable/partial status just changed during incremental update). */
    public static void clearCategory(RecipeCollection collection) {
        CATEGORY_CACHE.remove(stableKey(collection));
    }

    /**
     * Single point-of-control for the partial material marking feature.
     * All public methods check this before doing any work, so callers
     * never need to repeat the config gate.
     */
    private static boolean enabled() {
        return BetterRecipeBook.config.partialMarkingEnabled;
    }

    public static void beginFilteringUpdate(boolean active) {
        filteringActive = active;
        if (active) {
            if (filteringGeneration == Integer.MAX_VALUE) {
                // Generation counter wrapped — clear all caches to avoid stale comparisons
                PARTIAL_RECIPES.clear();
                CHECKED_COLLECTIONS.clear();
                filteringGeneration = 0;
            }
            filteringGeneration++;
        }
    }

    /**
     * Simple 64-bit hash of slot state (item presence + counts).
     */
    public static long slotHash(NonNullList<Slot> slots) {
        long h = 1;
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                h = 31 * h + (long)stack.getItem().hashCode();
                h = 31 * h + stack.getCount();
            }
        }
        return h;
    }

    public static Set<Item> hashInventory(NonNullList<Slot> slots) {
        Set<Item> inventoryItems = new HashSet<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryItems.add(stack.getItem());
            }
        }
        return inventoryItems;
    }

    /**
     * Checks all recipes in the collection and marks those that have some (but not all) matching ingredients.
     * Uses pre-hashed inventory set for O(1) ingredient lookup.
     */
    public static boolean markPartialMaterials(RecipeCollection collection, NonNullList<Slot> slots) {
        return markPartialMaterials(collection, hashInventory(slots));
    }

    /**
     * Checks all recipes in the collection using a pre-hashed inventory set.
     */
    public static boolean markPartialMaterials(RecipeCollection collection, Set<Item> inventoryItems) {
        if (!enabled()) return false;
        if (wasCheckedForPartialMaterials(collection)) return hasPartialMaterials(collection);

        List<ResourceLocation> key = stableKey(collection);
        CHECKED_COLLECTIONS.put(key, filteringGeneration);
        boolean markedAny = false;
        Set<ResourceLocation> partialRecipes = new HashSet<>();

        for (RecipeHolder<?> recipe : collection.getRecipes()) {
            // Skip recipes that are already fully craftable
            if (collection.isCraftable(recipe)) {
                continue;
            }

            Recipe<?> vanillaRecipe = recipe.value();
            if (hasMatchingIngredientFast(vanillaRecipe.getIngredients(), inventoryItems)) {
                partialRecipes.add(recipe.id());
                markedAny = true;
            }
        }

        if (markedAny) {
            PARTIAL_RECIPES.put(key, partialRecipes);
        } else {
            PARTIAL_RECIPES.remove(key);
        }

        return markedAny;
    }

    public static void markPartialMaterial(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return;
        List<ResourceLocation> key = stableKey(collection);
        CHECKED_COLLECTIONS.put(key, filteringGeneration);
        PARTIAL_RECIPES.put(key, new HashSet<>(Collections.singleton(recipeId)));
    }

    public static boolean wasCheckedForPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        Integer generation = CHECKED_COLLECTIONS.get(stableKey(collection));
        return filteringActive && generation != null && generation == filteringGeneration;
    }

    public static boolean isPartiallyCraftable(RecipeCollection collection, RecipeHolder<?> recipe) {
        return isPartiallyCraftable(collection, recipe.id());
    }

    public static boolean isPartiallyCraftable(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return false;
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(stableKey(collection));
        return partialRecipes != null && partialRecipes.contains(recipeId);
    }

    public static boolean hasPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(stableKey(collection));
        return partialRecipes != null && !partialRecipes.isEmpty();
    }

    /**
     * Removes a single recipe from the partial-materials set for a collection.
     * Used by the 3×3 grid cleanup step to prevent partial-recipe degradation
     * loops — recipes that need a 3×3 grid are never injected into craftable
     * when showAllRecipesInSurvival is off, but markPartialMaterials can still
     * tag them as partial.  Removing them here breaks the cycle.
     */
    public static void unmarkPartial(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return;
        List<ResourceLocation> key = stableKey(collection);
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(key);
        if (partialRecipes != null) {
            partialRecipes.remove(recipeId);
            if (partialRecipes.isEmpty()) {
                PARTIAL_RECIPES.remove(key);
            }
        }
    }

    /**
     * Classifies a collection by iterating its recipes and checking each
     * against both {@link #isPartiallyCraftable} and the vanilla craftable
     * set.  This is the single source of truth for collection classification;
     * sort methods and button mixins should use this instead of inline loops.
     */
    public static CollectionCategory categorize(RecipeCollection c) {
        if (!enabled()) return CollectionCategory.UNASSIGNED;

        List<ResourceLocation> key = stableKey(c);
        CollectionCategory cached = CATEGORY_CACHE.get(key);
        if (cached != null) return cached;

        boolean truly = false, partial = false;
        for (RecipeHolder<?> holder : c.getRecipes()) {
            if (isPartiallyCraftable(c, holder)) {
                partial = true;
            } else if (c.isCraftable(holder)) {
                truly = true;
            }
        }
        CollectionCategory result;
        if (truly) result = CollectionCategory.TRULY_CRAFTABLE;
        else if (partial) result = CollectionCategory.PARTIAL;
        else result = CollectionCategory.UNASSIGNED;

        CATEGORY_CACHE.put(key, result);
        return result;
    }

    public static List<RecipeHolder<?>> getPartiallyCraftableRecipes(RecipeCollection collection) {
        if (!enabled()) return Collections.emptyList();
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(stableKey(collection));
        if (partialRecipes == null || partialRecipes.isEmpty()) {
            return Collections.emptyList();
        }

        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (RecipeHolder<?> recipe : collection.getRecipes()) {
            if (partialRecipes.contains(recipe.id())) {
                recipes.add(recipe);
            }
        }

        return recipes;
    }

    /**
     * Legacy matching using raw slots (slower, kept for backward compat).
     */
    private static boolean hasMatchingIngredient(List<Ingredient> ingredients, NonNullList<Slot> slots) {
        Set<Item> inventoryItems = hashInventory(slots);
        return hasMatchingIngredientFast(ingredients, inventoryItems);
    }

    /**
     * Fast matching using pre-hashed inventory set — O(1) per ingredient.
     */
    private static boolean hasMatchingIngredientFast(List<Ingredient> ingredients, Set<Item> inventoryItems) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            // ingredient.getItems() returns all possible ItemStacks for this ingredient
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty() && inventoryItems.contains(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}
