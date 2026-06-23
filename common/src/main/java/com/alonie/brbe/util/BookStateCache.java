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
 * saved keyed by {@code (screenClass, slotHash, variant)}.  When the same
 * screen type reopens with the same inventory and variant, the cached list
 * is restored and the entire {@code updateCollections} pipeline is skipped.
 *
 * <p>The {@code variant} parameter distinguishes different filtering contexts
 * within the same screen — e.g. RBIP creative-mode tabs.  Use {@code null}
 * when no variant is active.
 *
 * <p>Cache entries are invalidated when:
 * <ul>
 *   <li>The slot hash doesn't match (inventory changed)</li>
 *   <li>The variant doesn't match (different creative tab)</li>
 *   <li>{@link #clear()} is called via recipe reload hooks</li>
 * </ul>
 */
public final class BookStateCache {

    // Sentinel for null variant keys (HashMap disallows null keys).
    private static final String NULL_VARIANT = new String("<none>");

    // screenClass → (variant → CachedState)
    private static final Map<Class<?>, Map<String, CachedState>> CACHE = new HashMap<>();

    private BookStateCache() {}

    /**
     * Returns the cached collection list if both the slot hash and variant
     * match, or null.
     */
    public static List<RecipeCollection> get(Class<?> screenClass, long slotHash, Object variant) {
        Map<String, CachedState> inner = CACHE.get(screenClass);
        if (inner == null) return null;
        CachedState state = inner.get(variantKey(variant));
        if (state != null && state.slotHash == slotHash) {
            return state.collections;
        }
        return null;
    }

    /**
     * Saves a snapshot of the current page results.  The list is defensively
     * copied so later mutations don't corrupt the cache.
     */
    public static void put(Class<?> screenClass, long slotHash,
                           List<RecipeCollection> collections, Object variant) {
        CACHE.computeIfAbsent(screenClass, k -> new HashMap<>())
                .put(variantKey(variant),
                     new CachedState(slotHash, new ArrayList<>(collections)));
    }

    public static void clear() {
        CACHE.clear();
    }

    private static String variantKey(Object variant) {
        if (variant == null) return NULL_VARIANT;
        // Use identity hash + class name for a stable but distinct key.
        // CreativeModeTab objects are stable singletons so this is reliable.
        return variant.getClass().getName() + "@" + System.identityHashCode(variant);
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
