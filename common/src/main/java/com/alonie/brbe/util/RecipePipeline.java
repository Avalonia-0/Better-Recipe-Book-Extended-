package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.*;

/**
 * Unified recipe-book pipeline for 1.21.1.
 *
 * <h3>Two orthogonal public operations</h3>
 * <ol>
 *   <li>{@link #updateRecipeState(List, PipelineContext)} —
 *       material-level state.  Augments vanilla's {@code canCraft}
 *       results with BRBE partial/incompatible detection and the
 *       3×3-on-2×2-grid fixup.  Idempotent — safe to call multiple
 *       times per frame.  <b>Does not modify the list structure.</b></li>
 *   <li>{@link #prepareDisplay(List, PipelineContext)} —
 *       visibility filtering + display sorting.  Filters
 *       {@code fitsDimensions} by grid constraints, removes empty
 *       collections, sorts by pin/partial/filter state.
 *       <b>Returns a new list; the input list is not mutated.</b></li>
 * </ol>
 *
 * <h3>showAllRecipesInSurvival fix</h3>
 * <p>Vanilla {@code canCraft} rejects 3×3 recipes on the 2×2 inventory
 * grid even when the player has all ingredients.  {@code updateRecipeState}
 * re-evaluates those recipes against raw ingredient counts so they show
 * as "craftable" rather than "partial".</p>
 */
public final class RecipePipeline {

    private RecipePipeline() {}

    // ═══════════════════════════════════════════════════════════════════
    // Public — Operation 1: Update recipe material state
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Update material-level recipe state for every collection in the list.
     *
     * <p>Called from {@code @Redirect List.forEach} in
     * {@code updateCollections}, right after vanilla's {@code canCraft}
     * evaluation.  Also called defensively from {@code initVisuals TAIL}
     * to cover tab-switch edge cases.</p>
     *
     * <p>Idempotent — safe to call multiple times on the same collections.
     * Does NOT mutate the list structure; only per-collection state
     * (craftable set, partial tags, incompatible tags) is updated.</p>
     *
     * <p>Steps:
     * <ol>
     *   <li>Cleanup: if partialMarkingEnabled OFF, remove partial state</li>
     *   <li>Incompatible marking: dimension/biome checks</li>
     *   <li>Partial marking + pre-check:
     *     <ul>
     *       <li>When partialMarkingEnabled ON + inventory changed:
     *         clear stale → pre-check (3×3 on 2×2) → markAndInject</li>
     *       <li>Otherwise, when on inventory screen with showAll:
     *         pre-check only (3×3 full-material → craftable set)</li>
     *     </ul>
     *   </li>
     * </ol>
     */
    // One-shot diagnostic flag — logs conditions on first call
    private static boolean brbe$diagnosticLogged = false;

    public static void updateRecipeState(List<RecipeCollection> collections,
                                          PipelineContext ctx) {
        if (collections == null || collections.isEmpty()) return;

        // -- One-shot diagnostic: log conditions on first onInventory call -
        if (!brbe$diagnosticLogged && ctx.onInventoryScreen) {
            brbe$diagnosticLogged = true;
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] updateRecipeState on inventory screen: "
                    + "showAll={}, partialMarking={}, inventoryChanged={}, "
                    + "menuSlots={}, collectionCount={}",
                    ctx.showAllRecipesInSurvival,
                    ctx.partialMarkingEnabled, ctx.inventoryChanged,
                    ctx.menuSlots != null ? ctx.menuSlots.size() : 0,
                    collections.size());
        }

        // -- Step 1: Cleanup when partialMarkingEnabled OFF --------------
        if (!ctx.partialMarkingEnabled) {
            for (RecipeCollection coll : collections) {
                if (!PartialCraftingUtil.hasPartialMaterialsRaw(coll)) continue;
                RecipeCollectionAccessor ca = (RecipeCollectionAccessor) coll;
                for (RecipeHolder<?> holder : coll.getRecipes()) {
                    if (PartialCraftingUtil.isPartiallyCraftableRaw(coll, holder.id())) {
                        ca.brbe$getCraftable().remove(holder);
                    }
                }
            }
            PartialCraftingUtil.invalidateCaches();
        }

        // -- Step 2: Incompatible marking ---------------------------------
        if (ctx.onInventoryScreen && ctx.showAllRecipesInSurvival) {
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }

        // -- Step 3: Partial marking + pre-check --------------------------
        // The pre-check (elevating 3×3 full-material recipes on the 2×2
        // inventory grid) must run regardless of partialMarkingEnabled —
        // it's about showAllRecipesInSurvival, not partial marking.
        if (ctx.partialMarkingEnabled && ctx.inventoryChanged) {
            PartialCraftingUtil.beginFilteringUpdate(true);

            // Step 3a: clear previously-injected partial recipes from craftable set
            // Only needed on the inventory (2×2) screen where the pre-check
            // injects 3×3 recipes.  On the crafting table (3×3), vanilla
            // canCraft correctly populates the craftable set, and clearing
            // based on stale partial data from a previous inventory-screen
            // visit would incorrectly remove genuinely craftable recipes.
            if (ctx.onInventoryScreen && ctx.showAllRecipesInSurvival) {
                for (RecipeCollection coll : collections) {
                    if (!PartialCraftingUtil.hasPartialMaterialsEvenIfStale(coll)) continue;
                    RecipeCollectionAccessor ca = (RecipeCollectionAccessor) coll;
                    for (RecipeHolder<?> holder : coll.getRecipes()) {
                        if (PartialCraftingUtil.isPartiallyCraftableEvenIfStale(coll, holder.id())) {
                            ca.brbe$getCraftable().remove(holder);
                        }
                    }
                }
            }

            // Step 3b (pre-check): elevate 3×3 full-material recipes
            // Runs AFTER clearing stale partial but BEFORE markAndInject.
            // At this point, isCraftable() is still false for 3×3 recipes
            // (vanilla rejected them), so the pre-check sees them correctly.
            // markPartialMaterials (in markAndInject) then sees
            // isCraftable=true and skips them — they won't be tagged partial.
            if (ctx.onInventoryScreen && ctx.showAllRecipesInSurvival) {
                brbe$preCheck(collections, ctx);
            }

            // Step 3c: mark + inject fresh partial recipes
            for (RecipeCollection coll : collections) {
                PartialCraftingUtil.markAndInject(coll, ctx.inventoryItems);
            }

            PartialCraftingUtil.beginFilteringUpdate(false);
        } else if (ctx.onInventoryScreen && ctx.showAllRecipesInSurvival) {
            // Run pre-check on inventory screen with showAll.
            // Covers two cases:
            // 1. partialMarkingEnabled=false → only pre-check (no marking)
            // 2. partialMarkingEnabled=true + inventoryChanged=false →
            //    "first open after startup" catch-up (state is current)
            brbe$preCheck(collections, ctx);
        }
    }

    /**
     * Elevates 3×3 recipes that have all ingredients to the craftable set.
     *
     * <p>Vanilla {@code canCraft} rejects 3×3 recipes on the 2×2 inventory
     * grid even when the player has all ingredients.  This fixup re-evaluates
     * those recipes against raw item counts and adds them to the craftable
     * set.  Any stale partial tag is also cleared.</p>
     */
    private static void brbe$preCheck(List<RecipeCollection> collections,
                                       PipelineContext ctx) {
        if (!ctx.onInventoryScreen || !ctx.showAllRecipesInSurvival) return;

        int total3x3 = 0, skippedCraftable = 0, elevated = 0;
        for (RecipeCollection coll : collections) {
            RecipeCollectionAccessor ca = (RecipeCollectionAccessor) coll;
            for (RecipeHolder<?> holder : coll.getRecipes()) {
                if (!brbe$needsLargerGrid(holder)) continue;
                total3x3++;
                if (coll.isCraftable(holder)) { skippedCraftable++; continue; }
                if (brbe$hasAllIngredients(holder.value(), ctx)) {
                    ca.brbe$getCraftable().add(holder);
                    PartialCraftingUtil.removePartialRecipe(coll, holder.id());
                    elevated++;
                }
            }
        }
        com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                "[BRBE-DIAG] pre-check: total3x3={} skippedCraftable={} elevated={}",
                total3x3, skippedCraftable, elevated);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public — Operation 2: Prepare display list
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Filter visibility and sort collections for display.
     *
     * <p>Called from {@code @Redirect page.updateCollections} and
     * {@code @Inject initVisuals TAIL}.  Does NOT recompute recipe
     * material state — assumes {@link #updateRecipeState} has already
     * been called on these collections.</p>
     *
     * @param collections the list to filter and sort (not mutated)
     * @param ctx         pipeline context
     * @return a new filtered, sorted list
     */
    public static List<RecipeCollection> prepareDisplay(
            List<RecipeCollection> collections,
            PipelineContext ctx) {

        if (collections == null || collections.isEmpty()) {
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] prepareDisplay: input collections is null or empty");
            return collections;
        }

        // -- Visibility ------------------------------------------------
        List<RecipeCollection> visible = applyVisibility(collections, ctx);

        if (visible.isEmpty()) {
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] prepareDisplay: applyVisibility returned EMPTY! "
                    + "input={} onInventory={} showAll={}",
                    collections.size(), ctx.onInventoryScreen,
                    ctx.showAllRecipesInSurvival);
        }

        // -- Sorting ---------------------------------------------------
        List<RecipeCollection> result = applySorting(visible, ctx);

        if (result.isEmpty() && !visible.isEmpty()) {
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] prepareDisplay: applySorting emptied the list! "
                    + "visible={} isFiltering={} partialMarking={}",
                    visible.size(), ctx.isFiltering, ctx.partialMarkingEnabled);
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Visibility — grid-aware display filtering
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Applies grid-size visibility rules to {@code fitsDimensions}
     * and filters out collections with nothing to display.
     *
     * <p>This is the <b>single place</b> where
     * {@code showAllRecipesInSurvival} affects display logic.</p>
     *
     * <h3>Decision table</h3>
     * <pre>
     *   Status × GridFit × showAll → DisplayMode
     *   ─────────────────────────────────────────
     *   FULLY_MATCHED  + FITS          → visible
     *   PARTIALLY      + FITS          → visible (partial overlay)
     *   UNMATCHED      + FITS          → visible (dim)
     *   FULLY_MATCHED  + LARGER + ON   → visible (no ghost)
     *   PARTIALLY      + LARGER + ON   → visible (partial overlay)
     *   UNMATCHED      + LARGER + ON   → visible (dim)
     *   ANY            + LARGER + OFF  → HIDE (remove from fits)
     * </pre>
     */
    private static List<RecipeCollection> applyVisibility(
            List<RecipeCollection> collections,
            PipelineContext ctx) {

        boolean showAll = ctx.showAllRecipesInSurvival;
        boolean retainPartial = ctx.partialMarkingEnabled;

        // Work on a copy to avoid mutating the original list
        List<RecipeCollection> working = new ArrayList<>(collections);

        if (!ctx.onInventoryScreen) {
            // Crafting table (3×3): all recipes fit the 3×3 grid, so
            // forcibly repopulate fitsDimensions.  Vanilla canCraft may
            // leave fitsDimensions stale when called with outdated
            // stackedContents (e.g. after shift-click crafting).
            int totalRecipes = 0, emptyFit = 0, repopulated = 0;
            for (RecipeCollection c : working) {
                var ca = (RecipeCollectionAccessor) c;
                int recipeCount = c.getRecipes().size();
                totalRecipes += recipeCount;
                if (ca.getFitsDimensions().isEmpty()) {
                    emptyFit++;
                }
                ca.getFitsDimensions().clear();
                ca.getFitsDimensions().addAll(c.getRecipes());
                repopulated += ca.getFitsDimensions().size();
            }
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] applyVisibility crafting-table: "
                    + "collections={} totalRecipes={} hadEmptyFit={} "
                    + "finalFitEntries={} invChanged={} sortEnabled={}",
                    working.size(), totalRecipes, emptyFit,
                    repopulated, ctx.inventoryChanged, ctx.brbeSortEnabled);
            return working;
        }

        // Inventory screen (2×2): apply grid-size filtering
        for (RecipeCollection c : working) {
            var ca = (RecipeCollectionAccessor) c;

            // When fitsDimensions is empty (vanilla cleared it because no
            // recipes fit the 2×2 grid), populate with all recipes so the
            // collection stays visible.  When showAll=ON, 3×3 recipes
            // should be shown.  When showAll=OFF, the filter below removes
            // them.
            if (ca.getFitsDimensions().isEmpty()) {
                ca.getFitsDimensions().addAll(c.getRecipes());
            }

            // Filter fitsDimensions: remove 3×3 recipes when showAll=OFF.
            // Use clear+addAll to handle ImmutableSet from vanilla.
            Set<RecipeHolder<?>> fits = ca.getFitsDimensions();
            if (!fits.isEmpty() && !showAll) {
                List<RecipeHolder<?>> keep = new ArrayList<>();
                for (RecipeHolder<?> holder : fits) {
                    if (!brbe$needsLargerGrid(holder)) {
                        keep.add(holder);
                    }
                }
                fits.clear();
                fits.addAll(keep);
            }
        }

        // Remove collections with nothing to display
        var iter = working.iterator();
        while (iter.hasNext()) {
            var ca = (RecipeCollectionAccessor) iter.next();
            if (ca.getFitsDimensions().isEmpty()) {
                iter.remove();
            }
        }

        // Clean up 3×3 partial entries when showAll=OFF.
        // These recipes are hidden (filtered out of fitsDimensions),
        // but their partial tags linger and can cause stale state.
        if (!showAll && retainPartial) {
            for (RecipeCollection c : working) {
                if (!PartialCraftingUtil.hasPartialMaterials(c)) continue;
                RecipeCollectionAccessor ca = (RecipeCollectionAccessor) c;
                for (RecipeHolder<?> holder : c.getRecipes()) {
                    if (PartialCraftingUtil.isPartiallyCraftable(c, holder.id())
                            && brbe$needsLargerGrid(holder)) {
                        ca.brbe$getCraftable().remove(holder);
                        PartialCraftingUtil.removePartialRecipe(c, holder.id());
                    }
                }
            }
        }

        return working;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Sorting — order for display
    // ═══════════════════════════════════════════════════════════════════

    private static List<RecipeCollection> applySorting(
            List<RecipeCollection> collections,
            PipelineContext ctx) {

        List<RecipeCollection> working = new ArrayList<>(collections);

        // -- Vanilla "show craftable only" toggle ----------------------
        // When the player toggles the vanilla filter ON, sort craftable
        // recipes before partial-material ones, and remove completely
        // uncraftable recipes.  applyPartialSort internally puts pinned
        // recipes first, so no explicit applyPins needed here.
        if (ctx.isFiltering) {
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] applySorting: isFiltering=true — "
                    + "sort craftable→partial + filter uncraftable");
            working = CollectionPipeline.applyPartialSort(
                    working, ctx.partialMarkingEnabled);
            working = CollectionPipeline.applyFilterToggle(working, true);
            return working;
        }

        // -- BRBE managed sort -----------------------------------------
        if (ctx.brbeSortEnabled) {
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] applySorting: RUNNING (brbeSortEnabled=true,"
                    + " partialMarking={})", ctx.partialMarkingEnabled);
            // When partialMarkingEnabled=true: three-way sort
            // (TRULY_CRAFTABLE → PARTIAL → UNASSIGNED).
            // When false: two-way sort (craftable → uncraftable) via
            // vanilla hasCraftable() — partial data isn't computed.
            working = CollectionPipeline.applyPartialSort(
                    working, ctx.partialMarkingEnabled);
            // Forced false when BRBE manages state; filter button is hidden.
            working = CollectionPipeline.applyFilterToggle(working, false);
            return working;
        }

        // -- Vanilla order — but pins ALWAYS go first ------------------
        com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                "[BRBE-DIAG] applySorting: pins only (vanilla order)");
        CollectionPipeline.applyPins(working);
        return working;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private static boolean brbe$needsLargerGrid(RecipeHolder<?> holder) {
        return PartialCraftingUtil.needsLargerGrid(holder);
    }

    /**
     * Checks whether the player has sufficient items to satisfy ALL
     * ingredients of a recipe, ignoring grid constraints.
     *
     * <p>Builds a temporary inventory map from menu slots, then consumes
     * items per-ingredient.  Each ingredient only needs ONE matching item
     * stack (not the full count), but the same item can't satisfy two
     * different ingredients — the consumption is tracked.</p>
     *
     * <p>This is used to distinguish "has all materials but grid is
     * too small" from "genuinely missing materials" for 3×3 recipes
     * shown on the 2×2 inventory screen.</p>
     */
    private static boolean brbe$hasAllIngredients(
            net.minecraft.world.item.crafting.Recipe<?> recipe,
            PipelineContext ctx) {

        List<Ingredient> ingredients;
        if (recipe instanceof ShapedRecipe shaped) {
            ingredients = shaped.getIngredients();
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            ingredients = shapeless.getIngredients();
        } else {
            return false;
        }

        // Build mutable inventory: item → available count
        Map<Item, Integer> available = new HashMap<>();
        for (var slot : ctx.menuSlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        // Consume one item per non-empty ingredient
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;

            boolean matched = false;
            for (ItemStack candidate : ingredient.getItems()) {
                if (candidate.isEmpty()) continue;
                int count = available.getOrDefault(candidate.getItem(), 0);
                if (count > 0) {
                    available.put(candidate.getItem(), count - 1);
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                return false; // at least one ingredient cannot be satisfied
            }
        }

        return true;
    }
}
