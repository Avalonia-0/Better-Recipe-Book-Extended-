package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

import java.util.*;

/**
 * Generic tagging system for {@link RecipeCollection} objects.
 *
 * Uses {@link WeakHashMap} to avoid preventing garbage collection of
 * RecipeCollection instances that are no longer referenced by the UI.
 * Generation tracking invalidates stale entries across rebuildCollections
 * cycles without explicit cleanup.
 *
 * <h3>Generation-aware queries</h3>
 * {@link #hasAnyTag} and {@link #hasTag} return {@code false} for data that
 * was marked in a previous generation — only the current generation's
 * markings are visible.  This prevents stale partial/incompatible overlays
 * from persisting after inventory changes.
 *
 * <p>Step 0 of the partial-marking cycle <em>intentionally</em> reads stale
 * data (to know which IDs to remove from the craftable set).  Use
 * {@link #hasAnyTagEvenIfStale} / {@link #hasTagEvenIfStale} for that case.
 *
 * @param <T> the tag type (e.g. {@code RecipeDisplayId} for partial/incompatible marking)
 */
public final class RecipeCollectionTagger<T> {
    private final WeakHashMap<RecipeCollection, Set<T>> tags = new WeakHashMap<>();
    private final WeakHashMap<RecipeCollection, Integer> checkedGenerations = new WeakHashMap<>();
    private int currentGeneration;

    // ---- Lifecycle ----

    /**
     * Begin a new filtering phase.  Increments the generation counter so
     * that stale data from previous generations is invisible to
     * generation-aware queries.
     */
    public void beginFiltering(boolean active) {
        if (active) {
            currentGeneration++;
        }
        // active=false is a no-op: generation persists across phases so
        // generation-aware queries can detect staleness at any time.
    }

    // ---- Generation / freshness ----

    /**
     * Returns true if {@code collection} was already checked during the
     * <em>current</em> filtering generation.  Callers use this to avoid
     * redundant computation.
     */
    public boolean wasChecked(RecipeCollection collection) {
        Integer gen = checkedGenerations.get(collection);
        return gen != null && gen == currentGeneration;
    }

    /** Mark {@code collection} as having been checked in the current generation. */
    public void markAsChecked(RecipeCollection collection) {
        checkedGenerations.put(collection, currentGeneration);
    }

    // ---- Generation-aware tag queries (safe for general use) ----

    /** True if the collection has at least one tag from the <em>current</em> generation. */
    public boolean hasAnyTag(RecipeCollection collection) {
        if (!wasChecked(collection)) return false;
        Set<T> set = tags.get(collection);
        return set != null && !set.isEmpty();
    }

    /** True if the collection has the specific tag from the <em>current</em> generation. */
    public boolean hasTag(RecipeCollection collection, T tag) {
        if (!wasChecked(collection)) return false;
        Set<T> set = tags.get(collection);
        return set != null && set.contains(tag);
    }

    /** Returns tags from the current generation, or an empty set if stale. */
    public Set<T> getTags(RecipeCollection collection) {
        if (!wasChecked(collection)) return Collections.emptySet();
        Set<T> set = tags.get(collection);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    // ---- Stale-data queries (for Step 0 — intentionally reads previous generation) ----

    /** True if the collection has at least one tag, <em>even if from a previous generation</em>. */
    public boolean hasAnyTagEvenIfStale(RecipeCollection collection) {
        Set<T> set = tags.get(collection);
        return set != null && !set.isEmpty();
    }

    /** True if the collection has the specific tag, <em>even if from a previous generation</em>. */
    public boolean hasTagEvenIfStale(RecipeCollection collection, T tag) {
        Set<T> set = tags.get(collection);
        return set != null && set.contains(tag);
    }

    /** Returns tags regardless of generation. */
    public Set<T> getTagsEvenIfStale(RecipeCollection collection) {
        Set<T> set = tags.get(collection);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    // ---- Tag writes ----

    /** Add a single tag for the current generation. */
    public void addTag(RecipeCollection collection, T tag) {
        tags.computeIfAbsent(collection, k -> new HashSet<>()).add(tag);
    }

    /** Replace all tags for a collection with a new set (current generation). */
    public void setAllTags(RecipeCollection collection, Set<T> newTags) {
        tags.put(collection, new HashSet<>(newTags));
    }

    /** Remove a single tag.  Cleans up the collection entry if the set becomes empty. */
    public void removeTag(RecipeCollection collection, T tag) {
        Set<T> set = tags.get(collection);
        if (set != null) {
            set.remove(tag);
            if (set.isEmpty()) {
                tags.remove(collection);
                checkedGenerations.remove(collection);
            }
        }
    }

    /**
     * Remove all tags for a collection without clearing its checked-generation
     * mark.  Use this when a collection is re-evaluated and found to have
     * no tags in the current generation.
     */
    public void clearTags(RecipeCollection collection) {
        tags.remove(collection);
        checkedGenerations.remove(collection);
    }

    /** Remove both tags and the checked-generation mark. */
    public void clearAll(RecipeCollection collection) {
        tags.remove(collection);
        checkedGenerations.remove(collection);
    }

    /** Remove all state — tags and generation marks for every collection. */
    public void clearAll() {
        tags.clear();
        checkedGenerations.clear();
    }

    /** Clear only checked-generation marks.  Preserves tag data so that

     * stale-data queries (Raw / EvenIfStale) can still find previously-

     * injected entries for cleanup.  Call this instead of {@link #clearAll()}

     * when you want to force re-evaluation without losing cleanup targets. */
    public void clearCheckedGenerations() {

        checkedGenerations.clear();

    }
}
