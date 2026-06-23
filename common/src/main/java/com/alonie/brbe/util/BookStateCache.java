package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists recipe book page results across screen close/reopen cycles.
 *
 * <p>When the inventory screen closes, the filtered/sorted collection list is
 * saved keyed by screen class + inventory slot hash.  When the same screen
 * type reopens with the same inventory, the cached list is restored and the
 * entire {@code updateCollections} pipeline is skipped.
 *
 * <p>Cache entries are invalidated when:
 * <ul>
 *   <li>The slot hash doesn't match (inventory changed)</li>
 *   <li>{@link #clear()} is called via recipe reload hooks</li>
 * </ul>
 */
public final class BookStateCache {

    private static final Map<Class<?>, CachedState> CACHE = new HashMap<>();

    private BookStateCache() {}

    /**
     * Returns the cached collection list if the slot hash matches, or null.
     */
    public static List<RecipeCollection> get(Class<?> screenClass, long slotHash) {
        CachedState state = CACHE.get(screenClass);
        if (state != null && state.slotHash == slotHash) {
            return state.collections;
        }
        return null;
    }

    /**
     * Saves a snapshot of the current page results.  The list is defensively
     * copied so later mutations don't corrupt the cache.
     */
    public static void put(Class<?> screenClass, long slotHash, List<RecipeCollection> collections) {
        CACHE.put(screenClass, new CachedState(slotHash, new ArrayList<>(collections)));
    }

    public static void clear() {
        CACHE.clear();
    }

    private static class CachedState {
        final long slotHash;
        final List<RecipeCollection> collections;

        CachedState(long slotHash, List<RecipeCollection> collections) {
            this.slotHash = slotHash;
            this.collections = collections;
        }
    }
}
