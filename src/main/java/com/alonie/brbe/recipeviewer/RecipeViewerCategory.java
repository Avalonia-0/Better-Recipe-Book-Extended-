package com.alonie.brbe.recipeviewer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * A viewer category: what the BRBE R/U overlay shows for a queried item.  The
 * crafting category (workbench) is the only one today; furnace / smithing / etc.
 * (and possibly non-recipe content like enchanting or fuel) register as new
 * categories and appear as tabs along the bottom of the overlay box.
 *
 * <p>Extension point: today {@link #query} returns recipe entries.  A future
 * non-recipe category would generalise this return type to an abstract display
 * item; keep it as {@code RecipeDisplayEntry} for now.</p>
 */
public interface RecipeViewerCategory {

    /** Stable identifier, e.g. {@code "crafting"}. */
    String id();

    /** Icon drawn inside the bottom tab. */
    ItemStack icon();

    /** Display name shown in the tab's tooltip. */
    Component name();

    /** R/U query for {@code target} (usage=true = recipes that use it). */
    List<RecipeDisplayEntry> query(ItemStack target, boolean usage);

    /** Whether this category can do anything with {@code target}. */
    default boolean appliesTo(ItemStack target) {
        return !target.isEmpty();
    }

    /** Whether this category's station is the currently open screen's menu
     *  (e.g. the crafting category for a crafting-table menu, the furnace
     *  category for furnace / blast-furnace / smoker menus). */
    default boolean appliesToMenu(AbstractContainerMenu menu) {
        return false;
    }

    /** Whether this category handles {@code target} as a workstation block:
     *  its usage view shows every recipe that uses the workstation as a
     *  condition (JEI semantics), regardless of the open screen. */
    default boolean appliesToStation(ItemStack target) {
        return false;
    }

    /**
     * Priority for picking the default tab on open.  Higher wins; return -1 to
     * rule this category out for {@code target}.  The default returns 0 for any
     * applicable target.
     */
    default int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 0 : -1;
    }

    /** Whether this is the fuel category (rendered standalone, no recipe
     *  buttons). */
    default boolean isFuelCategory() {
        return false;
    }

    /** Whether this category renders a standalone item grid (fuel / compost /
     *  info): no recipe buttons, no pins — hovering a grid cell shows the
     *  category's info lines in the tooltip.  Grid categories are exempt from
     *  the "hide objects of workstations without a recipe book" cuts exactly
     *  like the fuel category (they are info sheets, not station objects). */
    default boolean isGridCategory() {
        return false;
    }

    /** The item grid shown by a standalone grid category for the current
     *  query: e.g. a usage query of a fuel shows that fuel alone, a usage
     *  query of a fuel-burning workstation shows every fuel it can take.
     *  Empty = the category has nothing to show for this query.  Only
     *  consulted when {@link #isGridCategory()}. */
    default List<ItemStack> gridItems(ItemStack target, boolean usage) {
        return List.of();
    }

    /** EVERY object this category can show, query-independent — the Ctrl+O
     *  browse-all "import pool" (all queryable objects are gathered into the
     *  viewer).  Grid categories answer {@link #allGridItems()} instead. */
    default List<RecipeDisplayEntry> allEntries() {
        return List.of();
    }

    /** EVERY grid item of a standalone grid category (fuel / compost / info),
     *  query-independent — the Ctrl+O browse-all "import pool" for the
     *  category. */
    default List<ItemStack> allGridItems() {
        return List.of();
    }

    /** Workstation item icons that can produce {@code entry}, shown at the
     *  bottom of the recipe tooltip.  Built-in categories answer from their
     *  recipe-book category path; dynamic (mod) categories answer from the
     *  workstations they were registered with.  Empty means "fall back to the
     *  category icon".  The furnace category is exempt (its tooltip carries its
     *  per-station icons instead). */
    default List<ItemStack> stationIconsFor(RecipeDisplayEntry entry) {
        return List.of();
    }

    /** Whether this category has anything to show for {@code target}.  The
     *  default checks {@link #query}; the fuel category overrides this to
     *  answer from {@code usage && isFuel(target)}. */
    default boolean hasContent(ItemStack target, boolean usage) {
        return !query(target, usage).isEmpty();
    }
}
