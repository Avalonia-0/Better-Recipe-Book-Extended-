package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Incremental canCraft index for the recipe book (1.21.1 API).
 *
 * <p><b>Problem:</b> vanilla {@code RecipeBookComponent.updateCollections()}
 * walks every collection and re-runs
 * {@code RecipeCollection.canCraft(stackedContents, …)} — a full
 * O(recipes × ingredients) pass on every inventory change even when only one
 * item (e.g. a dragged stick) changed.  (BRBE's slotHash gate already skips
 * the whole pass when the inventory is unchanged; this index additionally
 * narrows a <em>changed</em> pass to only the collections whose ingredients
 * reference a changed item.)
 *
 * <p><b>Solution:</b> a reverse index {@code itemId → collections} plus a
 * snapshot of the last inventory {@code contents}.  On the next pass we diff
 * the inventory to get the changed-item set S; a collection whose ingredients
 * do not reference any item in S has an unchanged canCraft result for every
 * recipe, so its {@code canCraft} can be skipped (craftable set retained).
 *
 * <p><b>Correctness:</b> {@code canCraft} is a pure function of the recipe's
 * ingredients and the inventory contents map.  If {@code contents[X]} is
 * unchanged for every X in the collection's ingredients, the result for every
 * recipe in the collection is identical to the last pass.  The index is
 * rebuilt whenever {@code rebuildCollections} recreates the collection
 * objects; un-computed collections are always evaluated.
 */
public final class RecipeCraftingIndex {

    /** itemId (StackedContents.getStackingIndex) → collections whose recipes
     *  use that item as an ingredient.  A {@link Set} (not List) so the
     *  membership test is O(1): in a real modpack every recipe is distinct,
     *  so a commonly-used ingredient can be referenced by hundreds of
     *  collections and a List.contains scan would be O(collections × users). */
    private static final Map<Integer, Set<RecipeCollection>> INDEX = new java.util.HashMap<>();

    /** Collections already fully evaluated in the current generation.
     *  Weak: dropped when collections GC. */
    private static final WeakHashMap<RecipeCollection, Boolean> COMPUTED = new WeakHashMap<>();

    /** Snapshot of the last inventory contents (itemId → count). */
    private static it.unimi.dsi.fastutil.ints.Int2IntMap lastContents =
            new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

    /** Items whose count changed since {@link #lastContents}. */
    private static Set<Integer> changedItems = Set.of();

    /** Sentinel: collections whose ingredients could not be indexed are
     *  registered here so they are always re-evaluated on any change. */
    private static final int UNRESOLVED_INGREDIENT = -1;

    /** Grid/screen signature — when it changes, the selected predicate
     *  results may differ, so every collection must be re-evaluated. */
    private static int lastGridSignature;

    /** Version bumped on inventory change / rebuild; cache-invalidation
     *  signal for pipeline categorize caches. */
    private static int VERSION;

    /** Version bumped on rebuild. */
    private static int GENERATION;

    private RecipeCraftingIndex() {}

    /** Rebuild the reverse index from the current collection set.  Called
     *  after {@code rebuildCollections} recreates the collection objects. */
    public static void rebuild(List<RecipeCollection> allCollections) {
        INDEX.clear();
        COMPUTED.clear();
        lastContents = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        changedItems = Set.of();
        GENERATION++;
        VERSION++;
        for (RecipeCollection collection : allCollections) {
            for (RecipeHolder<?> holder : collection.getRecipes()) {
                for (Ingredient ingredient : holder.value().getIngredients()) {
                    if (ingredient.isEmpty()) continue;
                    try {
                        for (ItemStack stack : ingredient.getItems()) {
                            if (stack != null && !stack.isEmpty()) {
                                int id = StackedContents.getStackingIndex(stack);
                                INDEX.computeIfAbsent(id, k -> new HashSet<>()).add(collection);
                            }
                        }
                    } catch (Exception e) {
                        INDEX.computeIfAbsent(UNRESOLVED_INGREDIENT,
                                k -> new HashSet<>()).add(collection);
                    }
                }
            }
        }
    }

    /** Begin an inventory pass: diff current contents against the last
     *  snapshot and record the changed-item set.  Call before vanilla
     *  iterates collections so every {@link #shouldSkip} sees the same S. */
    public static void beginPass(StackedContents stacked, int gridSignature) {
        if (gridSignature != lastGridSignature) {
            COMPUTED.clear();
            lastGridSignature = gridSignature;
        }
        it.unimi.dsi.fastutil.ints.Int2IntMap current = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(stacked.contents);
        Set<Integer> changed = new HashSet<>();
        for (it.unimi.dsi.fastutil.ints.Int2IntMap.Entry e : current.int2IntEntrySet()) {
            int id = e.getIntKey();
            int count = e.getIntValue();
            if (!lastContents.containsKey(id) || lastContents.get(id) != count) {
                changed.add(id);
            }
        }
        for (int id : lastContents.keySet()) {
            if (!current.containsKey(id)) {
                changed.add(id);
            }
        }
        changedItems = changed;
        if (!changed.isEmpty()) {
            VERSION++;
        }
        lastContents = current;
    }

    /** True if {@code collection} can be skipped: already fully evaluated
     *  and none of its ingredients reference any changed item. */
    public static boolean shouldSkip(RecipeCollection collection) {
        if (!COMPUTED.containsKey(collection)) {
            return false;
        }
        if (changedItems.isEmpty()) {
            return true;
        }
        for (int item : changedItems) {
            Set<RecipeCollection> users = INDEX.get(item);
            if (users != null && users.contains(collection)) {
                return false;
            }
        }
        return true;
    }

    /** Mark {@code collection} as fully evaluated. */
    public static void markComputed(RecipeCollection collection) {
        COMPUTED.put(collection, Boolean.TRUE);
    }

    /** True if the last {@link #beginPass} diff found NO changed items. */
    public static boolean inventoryUnchanged() {
        return changedItems.isEmpty();
    }

    /** Current version (bumped on inventory change / rebuild). */
    public static int currentVersion() {
        return VERSION;
    }

    /** Rebuild generation (bumped on rebuild). */
    public static int generation() {
        return GENERATION;
    }
}
