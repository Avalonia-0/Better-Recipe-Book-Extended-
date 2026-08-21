package com.alonie.brbe.util;

import com.alonie.brbe.cache.VanillaRecipeCache;
import com.ava.test.TestRecipes;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.Map;

/**
 * Central lifecycle coordinator for {@link ClientRecipeBook#known} and
 * {@link RecipeCollection} mutations during a rebuildCollections cycle.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   ClientRecipeBook.rebuildCollections()
 *     → beginCycle(book, known)          // cache injection into known
 *     → [vanilla rebuilds collections]
 *     → For each RecipeBookComponent.updateCollections():
 *         → beginCollectionProcessing()  // partial + incompatible marking
 *         → [pipeline stages]
 *     → endCycle()
 * </pre>
 *
 * <p>This seam gives future work (pipeline unification, tagger consolidation)
 * a single entry point rather than spreading coordination across three mixins.
 */
public final class RecipeBookState {
    private static ClientRecipeBook currentBook;
    private static Map<RecipeDisplayId, RecipeDisplayEntry> currentKnown;
    private static int cycleDepth;

    private RecipeBookState() {}

    // ---- Lifecycle ----

    /**
     * Called at {@code ClientRecipeBook.rebuildCollections()} HEAD.
     * Runs cache injection (negative-ID entries) into the known map before
     * vanilla rebuilds collections from it.
     */
    public static void beginCycle(ClientRecipeBook book,
                                   Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        currentBook = book;
        currentKnown = known;
        cycleDepth++;

        // Phase 1: inject cached vanilla recipes if the server is sparse
        if (VanillaRecipeCache.hasEntries()) {
            VanillaRecipeCache.detectAndInject(book, known);
        }
        // Phase 1.5: JVM-gated test recipes (-Dava.test.recipes=N)
        TestRecipes.injectInto(known);
    }

    /**
     * Called at {@code RecipeBookComponent.updateCollections()} HEAD.
     * Signals that collection processing (partial marking, incompatible
     * marking) is about to begin.
     */
    public static void beginCollectionProcessing() {
        // Seam for Phase 3 pipeline — currently a no-op.
        // When the pipeline is unified, this becomes the entry point
        // where SearchFilter → UngroupSplit → PinSort → PartialSort
        // are registered and applied deterministically.
    }

    /**
     * Hook called after a single RecipeCollection has been processed by
     * PartialCraftingUtil and IncompatibleCraftingUtil.
     */
    public static void onCollectionProcessed(RecipeCollection collection) {
        // Future: verification hook — assert that a collection's craftable
        // set is internally consistent after all mutations.
    }

    /**
     * Called at {@code ClientRecipeBook.rebuildCollections()} RETURN.
     * Resets cycle state.
     */
    public static void endCycle() {
        cycleDepth--;
        if (cycleDepth <= 0) {
            currentBook = null;
            currentKnown = null;
            cycleDepth = 0;
        }
    }

    // ---- Guards ----

    /** True while a rebuildCollections cycle is active. */
    public static boolean isActive() {
        return cycleDepth > 0;
    }

    // ---- Coordinated known writes ----

    /**
     * Add an entry to the known map.  Prefer this over direct map access
     * so that all known writes flow through a single audit point.
     */
    public static void addToKnown(RecipeDisplayId id, RecipeDisplayEntry entry) {
        if (currentKnown != null) {
            currentKnown.put(id, entry);
        }
    }

    /**
     * Remove an entry from the known map.
     */
    public static void removeFromKnown(RecipeDisplayId id) {
        if (currentKnown != null) {
            currentKnown.remove(id);
        }
    }
}
