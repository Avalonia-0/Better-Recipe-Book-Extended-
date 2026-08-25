package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.StackedItemContentsAccessor;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Incremental canCraft index for the recipe book.
 *
 * <p><b>Problem:</b> vanilla {@code RecipeBookComponent.selectMatchingRecipes()}
 * (called on every recipe-book open AND every inventory change) walks every
 * tab → every collection → every recipe and re-runs
 * {@code RecipeDisplayEntry.canCraft(stackedContents)} — a full O(recipes ×
 * ingredients) pass even when only one item (e.g. a dragged stick) changed.
 *
 * <p><b>Solution:</b> a reverse index {@code item → collections} plus a
 * snapshot of the last inventory {@code amounts}.  On the next pass we diff
 * the inventory to get the set of <em>changed</em> items S; a collection whose
 * ingredients do not reference any item in S has an unchanged canCraft result
 * for every recipe — {@code RecipeCollection.selectRecipes} can be skipped for
 * it entirely, keeping the previous {@code craftable} set.
 *
 * <p><b>Correctness:</b> {@code canCraft} is a pure function of the recipe's
 * ingredients and the inventory {@code amounts} map.  If {@code amounts[X]} is
 * unchanged for every X that appears in the collection's ingredients, the
 * result for every recipe in the collection is identical to the last pass.
 * The index is rebuilt whenever {@code rebuildCollections} recreates the
 * collection objects, and any collection not yet fully evaluated (new
 * objects, or a grid/screen change that invalidates the {@code selected}
 * predicate) is always re-evaluated.
 */
public final class RecipeCraftingIndex {

    /** item → collections whose recipes use that item as an ingredient.
     *  A {@link Set} (not List) so {@link #shouldSkip}'s membership test is
     *  O(1): in a real modpack every recipe is distinct, so a commonly-used
     *  ingredient (e.g. planks) can be referenced by hundreds of collections
     *  and a List.contains scan would make every inventory pass O(collections
     *  × users-per-item). */
    private static final Map<Holder<Item>, Set<RecipeCollection>> INDEX = new java.util.HashMap<>();

    /** Collections already fully evaluated (craftable + selected populated)
     *  in the current generation.  Weak: dropped when collections GC. */
    private static final WeakHashMap<RecipeCollection, Boolean> COMPUTED = new WeakHashMap<>();

    /** Snapshot of the last inventory amounts (item → count). */
    private static Reference2IntOpenHashMap<Holder<Item>> lastAmounts =
            new Reference2IntOpenHashMap<>();

    /** Items whose count changed since {@link #lastAmounts} — computed once
     *  per inventory pass in {@link #beginPass}. */
    private static Set<Holder<Item>> changedItems = Set.of();

    /** Sentinel item: collections whose ingredients could not be fully
     *  indexed (custom ingredients with a failing item stream) are registered
     *  under this key so they are always re-evaluated when anything changes. */
    private static final Holder<Item> UNRESOLVED_INGREDIENT =
            Holder.direct(net.minecraft.world.item.Items.AIR);

    /** Grid/screen signature — when it changes, the {@code selected} predicate
     *  results may differ, so every collection must be re-evaluated. */
    private static int lastGridSignature;

    private RecipeCraftingIndex() {}

    /** Rebuild the reverse index from the current collection set.  Called
     *  after {@code rebuildCollections} recreates the collection objects —
     *  all previous COMPUTED marks are for dead objects, so they are cleared
     *  and the next pass is a full evaluation. */
    public static void rebuild(List<RecipeCollection> allCollections) {
        INDEX.clear();
        COMPUTED.clear();
        lastAmounts = new Reference2IntOpenHashMap<>();
        changedItems = Set.of();
        GENERATION++;
        VERSION++;
        for (RecipeCollection collection : allCollections) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                entry.craftingRequirements().ifPresent(ingredients -> {
                    for (Ingredient ingredient : ingredients) {
                        try {
                            ingredient.items().forEach(item -> {
                                if (item != null) {
                                    INDEX.computeIfAbsent(item, k -> new HashSet<>()).add(collection);
                                }
                            });
                        } catch (Exception e) {
                            // custom ingredient whose item stream fails —
                            // register under the sentinel so the collection is
                            // always re-evaluated on any inventory change
                            INDEX.computeIfAbsent(UNRESOLVED_INGREDIENT,
                                    k -> new HashSet<>()).add(collection);
                        }
                    }
                });
            }
        }
    }

    /** Begin an inventory pass: diff the current amounts against the last
     *  snapshot and record the changed-item set.  Called before vanilla
     *  iterates collections, so every {@link #shouldSkip} in this pass sees
     *  the same S.  Also invalidates COMPUTED when the grid/screen changed. */
    public static void beginPass(StackedItemContents stacked, int gridSignature) {
        if (gridSignature != lastGridSignature) {
            // grid/screen changed → selected predicate may differ → full pass
            COMPUTED.clear();
            lastGridSignature = gridSignature;
        }
        Reference2IntOpenHashMap<Holder<Item>> current = snapshotAmounts(stacked);
        Set<Holder<Item>> changed = new HashSet<>();
        // items in current but not in last, or with a different count
        for (var entry : current.reference2IntEntrySet()) {
            Holder<Item> item = entry.getKey();
            int count = entry.getIntValue();
            if (!lastAmounts.containsKey(item) || lastAmounts.getInt(item) != count) {
                changed.add(item);
            }
        }
        // items removed entirely
        for (Holder<Item> item : lastAmounts.keySet()) {
            if (!current.containsKey(item)) {
                changed.add(item);
            }
        }
        changedItems = changed;
        if (!changed.isEmpty()) {
            VERSION++;
        }
        lastAmounts = current;
    }

    /** True if {@code collection} can be skipped: already fully evaluated and
     *  none of its ingredients reference any changed item. */
    public static boolean shouldSkip(RecipeCollection collection) {
        if (!COMPUTED.containsKey(collection)) {
            return false;
        }
        if (changedItems.isEmpty()) {
            // inventory unchanged → every evaluated collection is still valid
            return true;
        }
        for (Holder<Item> item : changedItems) {
            Set<RecipeCollection> users = INDEX.get(item);
            if (users != null && users.contains(collection)) {
                return false;
            }
        }
        return true;
    }

    /** Mark {@code collection} as fully evaluated (craftable + selected
     *  populated) so future passes can skip it when unaffected. */
    public static void markComputed(RecipeCollection collection) {
        COMPUTED.put(collection, Boolean.TRUE);
    }

    /** True if any collection is not yet fully evaluated (forces the caller
     *  to run a full pass instead of an incremental one). */
    public static boolean hasUncomputed(RecipeCollection collection) {
        return !COMPUTED.containsKey(collection);
    }

    /** True if the last {@link #beginPass} diff found NO changed items —
     *  i.e. the inventory (and thus every collection's canCraft result) is
     *  unchanged since the previous pass.  When true, any cached pipeline
     *  output (pins order + partial sort) from that pass is still valid. */
    public static boolean inventoryUnchanged() {
        return changedItems.isEmpty();
    }

    /** Version counter bumped whenever the inventory contents change or the
     *  index is rebuilt — used by callers to invalidate caches that depend
     *  on collection craftable/partial state (e.g. pipeline categorize). */
    public static int currentVersion() {
        return VERSION;
    }

    private static int VERSION;

    /** Version counter incremented by {@link #rebuild} — used by callers to
     *  invalidate caches keyed on collection identities. */
    public static int generation() {
        return GENERATION;
    }

    private static int GENERATION;

    private static Reference2IntOpenHashMap<Holder<Item>> snapshotAmounts(
            StackedItemContents stacked) {
        Reference2IntOpenHashMap<Holder<Item>> copy = new Reference2IntOpenHashMap<>();
        StackedContents<Holder<Item>> raw =
                ((StackedItemContentsAccessor) stacked).brbe$getRaw();
        for (var entry : raw.amounts.reference2IntEntrySet()) {
            copy.put(entry.getKey(), entry.getIntValue());
        }
        return copy;
    }
}
