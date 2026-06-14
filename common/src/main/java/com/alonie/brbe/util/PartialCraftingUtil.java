package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class PartialCraftingUtil {
    private static final WeakHashMap<RecipeCollection, Set<ResourceLocation>> PARTIAL_RECIPES = new WeakHashMap<>();
    private static final WeakHashMap<RecipeCollection, Integer> CHECKED_COLLECTIONS = new WeakHashMap<>();
    private static int filteringGeneration;
    private static boolean filteringActive;

    private PartialCraftingUtil() {
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
            filteringGeneration++;
        }
    }

    /**
     * Checks all recipes in the collection and marks those that have some (but not all) matching ingredients.
     */
    public static boolean markPartialMaterials(RecipeCollection collection, NonNullList<Slot> slots) {
        if (!enabled()) return false;
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        boolean markedAny = false;
        Set<ResourceLocation> partialRecipes = new HashSet<>();

        for (RecipeHolder<?> recipe : collection.getRecipes()) {
            // Skip recipes that are already fully craftable
            if (collection.isCraftable(recipe)) {
                continue;
            }

            Recipe<?> vanillaRecipe = recipe.value();
            if (hasMatchingIngredient(vanillaRecipe.getIngredients(), slots)) {
                partialRecipes.add(recipe.id());
                markedAny = true;
            }
        }

        if (markedAny) {
            PARTIAL_RECIPES.put(collection, partialRecipes);
        } else {
            PARTIAL_RECIPES.remove(collection);
        }

        return markedAny;
    }

    public static void markPartialMaterial(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return;
        PARTIAL_RECIPES.put(collection, new HashSet<>(Collections.singleton(recipeId)));
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
    }

    public static boolean wasCheckedForPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        Integer generation = CHECKED_COLLECTIONS.get(collection);
        return filteringActive && generation != null && generation == filteringGeneration;
    }

    public static boolean isPartiallyCraftable(RecipeCollection collection, RecipeHolder<?> recipe) {
        return isPartiallyCraftable(collection, recipe.id());
    }

    public static boolean isPartiallyCraftable(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return false;
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(collection);
        return partialRecipes != null && partialRecipes.contains(recipeId);
    }

    public static boolean hasPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(collection);
        return partialRecipes != null && !partialRecipes.isEmpty();
    }

    public static void sortCraftableBeforePartial(List<RecipeCollection> collections) {
        if (!enabled()) return;
        List<RecipeCollection> craftableCollections = new ArrayList<>();
        List<RecipeCollection> partialCollections = new ArrayList<>();

        for (RecipeCollection collection : collections) {
            if (collection.hasCraftable()) {
                craftableCollections.add(collection);
            } else {
                partialCollections.add(collection);
            }
        }

        collections.clear();
        collections.addAll(craftableCollections);
        collections.addAll(partialCollections);
    }

    public static List<RecipeHolder<?>> getPartiallyCraftableRecipes(RecipeCollection collection) {
        if (!enabled()) return Collections.emptyList();
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(collection);
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

    private static boolean hasMatchingIngredient(List<Ingredient> ingredients, NonNullList<Slot> slots) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }

            for (Slot slot : slots) {
                if (slot.hasItem() && ingredient.test(slot.getItem())) {
                    return true;
                }
            }
        }

        return false;
    }
}
