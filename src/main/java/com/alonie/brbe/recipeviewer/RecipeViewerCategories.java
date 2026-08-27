package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of viewer categories.  Add new categories (furnace, smithing, …)
 * here and they automatically appear as bottom tabs on the overlay.  The
 * companion {@code zzzbrbe-jei-plugins} mod appends dynamic categories for each
 * mod recipe type via {@link #registerExternal}.
 */
public final class RecipeViewerCategories {

    private RecipeViewerCategories() {}

    /** Built-in categories (vanilla recipe types). */
    private static final List<RecipeViewerCategory> BUILTIN =
            List.of(new FurnaceRecipeCategory(), new CraftingRecipeCategory(),
                    new FuelRecipeCategory(), new StonecuttingRecipeCategory(),
                    new SmithingRecipeCategory(), new AnvilRecipeCategory(),
                    new BrewingRecipeCategory(), new GrindstoneRecipeCategory(),
                    new CompostRecipeCategory(), new InfoRecipeCategory());

    /** Categories appended by the companion mod (mod recipe types). */
    private static final List<RecipeViewerCategory> EXTERNAL = new CopyOnWriteArrayList<>();

    private static volatile List<RecipeViewerCategory> ALL;

    /** Set by the JEI plugin collector after each re-collection: the
     *  "category has no visible objects" computation (category-tab hiding) is
     *  stale until this flag is consumed by the overlay. */
    private static volatile boolean visibilityDirty = true;

    /** Mark the category-visibility computation stale (called after every
     *  plugin re-collection). */
    public static void markVisibilityDirty() {
        visibilityDirty = true;
    }

    /** Whether the visibility computation is stale; consuming resets it. */
    public static boolean consumeVisibilityDirty() {
        boolean dirty = visibilityDirty;
        visibilityDirty = false;
        return dirty;
    }

    /** All categories: built-in followed by externally registered ones. */
    public static List<RecipeViewerCategory> all() {
        List<RecipeViewerCategory> cached = ALL;
        if (cached == null) {
            cached = buildAll();
            ALL = cached;
        }
        return cached;
    }

    /** Registers categories collected from mod JEI plugins.  Idempotent. */
    public static void registerExternal(List<RecipeViewerCategory> categories) {
        if (categories == null || categories.isEmpty()) return;
        boolean changed = false;
        for (RecipeViewerCategory category : categories) {
            if (category != null && !EXTERNAL.contains(category)) {
                EXTERNAL.add(category);
                changed = true;
            }
        }
        if (changed) ALL = null;
    }

    private static List<RecipeViewerCategory> buildAll() {
        if (EXTERNAL.isEmpty()) return BUILTIN;
        List<RecipeViewerCategory> out = new ArrayList<>(BUILTIN);
        out.addAll(EXTERNAL);
        return List.copyOf(out);
    }

    /**
     * Pick the default category for {@code target} on open.  A workstation
     * block's usage view wins first (JEI semantics); otherwise the applicable
     * category with the highest {@link RecipeViewerCategory#defaultPriority}
     * whose query yields at least one entry.  The open screen's menu no longer
     * short-circuits the choice — an item usable in several categories (e.g.
     * planks: crafting + fuel) defaults to its most specific one (fuel) so the
     * multi-category uses are surfaced instead of being hidden behind the
     * current container.  Returns null when no category can show anything for
     * {@code target} (the viewer does not open).
     */
    public static RecipeViewerCategory defaultFor(ItemStack target, boolean usage,
                                                  AbstractContainerMenu menu) {
        if (usage) {
            RecipeViewerCategory firstMatch = null;
            for (RecipeViewerCategory category : all()) {
                if (!category.appliesToStation(target)) continue;
                // The "hide objects of workstations without a recipe book"
                // toggle cuts the workstation-category connection of an
                // illegal station: the grid categories (fuel / compost / info)
                // are exempt — a fuel-burning station still shows the fuel it
                // can take — and a legal (recipe-book-backed) station keeps
                // its categories.
                if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                        && !category.isGridCategory()
                        && !RecipeViewerEngine.isRecipeBookStation(target)) {
                    continue;
                }
                // A workstation may serve several categories (e.g. BetterEnd's
                // end stone smelter is registered both as a minecraft:blasting
                // catalyst and as a bclib:alloying crafting station).  Prefer
                // the first match that actually has content, falling back to
                // the first match when none does — otherwise the query lands on
                // an empty vanilla type (unlock-all off) instead of the mod
                // type's recipes.
                if (category.hasContent(target, true)) {
                    return category;
                }
                if (firstMatch == null) {
                    firstMatch = category;
                }
            }
            if (firstMatch != null) {
                // The station categories have no content (e.g. the smithing
                // table with no unlocked recipes).  Before settling on the
                // empty default — which drops the query to the external
                // viewer — prefer any category that CAN show this query: the
                // fuel tab for a burnable workstation (a workstation can also
                // be a fuel item, e.g. the smithing table).
                RecipeViewerCategory alternative = bestByPriority(target, usage);
                if (alternative != null) {
                    return alternative;
                }
                return firstMatch;
            }
        }
        return bestByPriority(target, usage);
    }

    /** The applicable category with the highest {@link RecipeViewerCategory
     *  #defaultPriority} whose query yields at least one entry, or null. */
    private static RecipeViewerCategory bestByPriority(ItemStack target, boolean usage) {
        RecipeViewerCategory best = null;
        int bestPriority = -1;
        for (RecipeViewerCategory category : all()) {
            int priority = category.defaultPriority(target);
            if (priority <= bestPriority) continue;
            if (category.hasContent(target, usage)) {
                best = category;
                bestPriority = priority;
            }
        }
        return best;
    }
}
