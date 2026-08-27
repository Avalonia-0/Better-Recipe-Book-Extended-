package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * A viewer category: what the BRBE R/U overlay shows for a queried item.  The
 * crafting category (workbench) is the only one today; furnace / smithing / etc.
 * (and possibly non-recipe content like enchanting or fuel) register as new
 * categories and appear as tabs along the bottom of the overlay box.
 *
 * <p>1.21.1 版：{@link #query} 返回 {@link RecipeHolder}（旧 Recipe 模型），
 * 无 1.21.11 的 RecipeDisplayEntry 体系。</p>
 */
public interface RecipeViewerCategory {

    /** Stable identifier, e.g. {@code "crafting"}. */
    String id();

    /** Icon drawn inside the bottom tab. */
    ItemStack icon();

    /** Display name shown in the tab's tooltip. */
    Component name();

    /** R/U query for {@code target} (usage=true = recipes that use it). */
    List<RecipeHolder<?>> query(ItemStack target, boolean usage);

    /** JEI-backed entries for {@code target} (empty by default).  Collected
     *  from JEI plugins / the embedded headless JEI runtime; rendered by the
     *  popup through {@code IRecipeManager#createRecipeLayoutDrawable}. */
    default List<RecipeViewerEngine.JeiEntry> queryJei(ItemStack target, boolean usage) {
        return List.of();
    }

    /** EVERY JEI entry this category can show, query-independent. */
    default List<RecipeViewerEngine.JeiEntry> allJeiEntries() {
        return List.of();
    }

    /** Whether this category can do anything with {@code target}. */
    default boolean appliesTo(ItemStack target) {
        return !target.isEmpty();
    }

    /** Whether this category's station is the currently open screen's menu
     *  (e.g. the crafting category for a crafting-table menu). */
    default boolean appliesToMenu(AbstractContainerMenu menu) {
        return false;
    }

    /** Whether this category handles {@code target} as a workstation block. */
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
     *  info): no recipe buttons, no pins. */
    default boolean isGridCategory() {
        return false;
    }

    /** The item grid shown by a standalone grid category for the current query. */
    default List<ItemStack> gridItems(ItemStack target, boolean usage) {
        return List.of();
    }

    /** EVERY recipe this category can show, query-independent (Ctrl+O
     *  browse-all "import pool"). */
    default List<RecipeHolder<?>> allEntries() {
        return List.of();
    }

    /** EVERY grid item of a standalone grid category. */
    default List<ItemStack> allGridItems() {
        return List.of();
    }

    /** Workstation item icons that can produce {@code entry}, shown at the
     *  bottom of the recipe tooltip. */
    default List<ItemStack> stationIconsFor(RecipeHolder<?> entry) {
        return List.of();
    }

    /** Whether this category has anything to show for {@code target}. */
    default boolean hasContent(ItemStack target, boolean usage) {
        return !query(target, usage).isEmpty() || !queryJei(target, usage).isEmpty();
    }
}
