package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-screen result cache for recipe book page lists.
 *
 * <p>Keyed by {@code (screenClass, slotHash, variant)} where variant
 * distinguishes RBIP creative tabs.  On cache hit, the pipeline skips
 * ALL data stages (forEach, partial marking, search, sort) and restores
 * the cached page list directly — giving instant repeated opens.
 *
 * <p><b>Cache safety:</b> The cache stores shallow copies of the
 * RecipeCollection list.  This is safe because:
 * <ul>
 *   <li>The cache is only used when inventory (slotHash) is unchanged</li>
 *   <li>When inventory changes, the cache misses and a full fresh pipeline
 *       run populates a new cache entry with updated state</li>
 *   <li>Without the (removed) incremental forEach path, RecipeCollection
 *       objects are never mutated between cache write and cache read</li>
 * </ul>
 */
public final class BookStateCache {

    private static final Map<Class<?>, Map<String, List<RecipeCollection>>> CACHE = new HashMap<>();

    private BookStateCache() {}

    public static List<RecipeCollection> get(Class<?> screenClass, long slotHash, Object variant) {
        return get(screenClass, slotHash, variant, false);
    }

    public static List<RecipeCollection> get(Class<?> screenClass, long slotHash,
                                              Object variant, boolean isFiltering) {
        Map<String, List<RecipeCollection>> screenCache = CACHE.get(screenClass);
        if (screenCache == null) return null;
        return screenCache.get(cacheKey(slotHash, variant, isFiltering));
    }

    public static void put(Class<?> screenClass, long slotHash,
                           List<RecipeCollection> collections, Object variant) {
        put(screenClass, slotHash, collections, variant, false);
    }

    public static void put(Class<?> screenClass, long slotHash,
                           List<RecipeCollection> collections, Object variant,
                           boolean isFiltering) {
        CACHE.computeIfAbsent(screenClass, k -> new HashMap<>())
              .put(cacheKey(slotHash, variant, isFiltering),
                   new ArrayList<>(collections)); // shallow copy — safe without incremental path
    }

    public static void clear() {
        CACHE.clear();
    }

    private static String cacheKey(long slotHash, Object variant) {
        return cacheKey(slotHash, variant, false);
    }

    private static String cacheKey(long slotHash, Object variant, boolean isFiltering) {
        return slotHash + "/" + (variant != null ? variant.hashCode() : "none")
                + "/filter=" + isFiltering
                + "/pc=" + BetterRecipeBook.config.partialCraftingEnabled
                + "/pm=" + BetterRecipeBook.config.partialMarkingEnabled;
    }
}
