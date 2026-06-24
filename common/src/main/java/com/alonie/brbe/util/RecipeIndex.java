package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reverse index mapping {@code Item → Set<RecipeCollection>} for fast
 * partial-material marking lookups.
 *
 * <p>Instead of iterating all ~25K collections every time inventory changes,
 * the partial-mark stage queries only the collections that actually use
 * items the player currently has — typically ~500-5000 in large modpacks.
 * This saves ~60ms on first runs.
 */
public final class RecipeIndex {

    private static final Map<Item, Set<RecipeCollection>> INDEX = new HashMap<>();
    private static volatile boolean built;

    private RecipeIndex() {}

    public static boolean isBuilt() {
        return built;
    }

    public static void build(Collection<RecipeCollection> allCollections) {
        if (built) return;
        synchronized (INDEX) {
            if (built) return;
            INDEX.clear();
            for (RecipeCollection coll : allCollections) {
                for (RecipeHolder<?> holder : coll.getRecipes()) {
                    var recipe = holder.value();
                    for (Ingredient ingredient : recipe.getIngredients()) {
                        for (var stack : ingredient.getItems()) {
                            Item item = stack.getItem();
                            INDEX.computeIfAbsent(item, k -> new HashSet<>()).add(coll);
                        }
                    }
                }
            }
            built = true;
        }
    }

    /**
     * Returns all collections that use at least one of the given items.
     */
    public static Set<RecipeCollection> getAffected(Set<Item> inventoryItems) {
        if (!built) return Collections.emptySet();
        Set<RecipeCollection> result = new HashSet<>();
        synchronized (INDEX) {
            for (Item item : inventoryItems) {
                Set<RecipeCollection> colls = INDEX.get(item);
                if (colls != null) {
                    result.addAll(colls);
                }
            }
        }
        return result;
    }

    public static void clear() {
        synchronized (INDEX) {
            INDEX.clear();
            built = false;
        }
    }
}
