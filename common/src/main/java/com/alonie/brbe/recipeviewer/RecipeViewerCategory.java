package com.alonie.brbe.recipeviewer;

import net.minecraft.network.chat.Component;
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

    /**
     * Priority for picking the default tab on open.  Higher wins; return -1 to
     * rule this category out for {@code target}.  The default returns 0 for any
     * applicable target.
     */
    default int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 0 : -1;
    }
}
