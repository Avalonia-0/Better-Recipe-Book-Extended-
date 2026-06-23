package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reverse index: {@code Item → Set<RecipeCollection>} mapping each item to
 * every RecipeCollection that uses it as an ingredient.
 *
 * <p>Built once from the player's {@code ClientRecipeBook} on the first
 * {@code updateCollections} call.  Rebuilt when recipes are reloaded.
 *
 * <p>Used by the incremental update path in the forEach redirect: when a
 * few inventory slots change, only the RecipeCollections that use the
 * changed items need re-evaluation.
 */
public final class RecipeIndex {

    private static final Map<Item, Set<RecipeCollection>> INDEX = new HashMap<>();
    private static boolean built;

    private RecipeIndex() {}

    /**
     * Builds the full reverse index from all recipe collections.
     * Safe to call multiple times — clears and rebuilds.
     */
    public static void build(Collection<RecipeCollection> allCollections) {
        INDEX.clear();
        for (RecipeCollection coll : allCollections) {
            for (RecipeHolder<?> holder : coll.getRecipes()) {
                for (Ingredient ing : holder.value().getIngredients()) {
                    if (ing.isEmpty()) continue;
                    for (ItemStack stack : ing.getItems()) {
                        if (!stack.isEmpty()) {
                            INDEX.computeIfAbsent(stack.getItem(), k -> new HashSet<>()).add(coll);
                        }
                    }
                }
            }
        }
        built = true;
    }

    /**
     * Returns the set of RecipeCollections that use any of the given items
     * as ingredients.  Empty set if none match.
     */
    public static Set<RecipeCollection> getAffected(Set<Item> changedItems) {
        Set<RecipeCollection> dirty = new HashSet<>();
        for (Item item : changedItems) {
            Set<RecipeCollection> colls = INDEX.get(item);
            if (colls != null) {
                dirty.addAll(colls);
            }
        }
        return dirty;
    }

    public static boolean isBuilt() {
        return built;
    }

    public static int size() {
        return INDEX.size();
    }

    public static void clear() {
        INDEX.clear();
        built = false;
    }
}
