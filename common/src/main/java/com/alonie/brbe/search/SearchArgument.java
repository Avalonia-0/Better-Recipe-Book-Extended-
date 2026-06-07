package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

/**
 * A single search condition. Matches an ItemStack against the condition.
 */
public interface SearchArgument {
    /**
     * Returns true if the given ItemStack matches this search condition.
     */
    boolean matches(ItemStack stack, SearchCache cache);

    /**
     * Returns true if this condition uses any advanced search syntax
     * (i.e., anything beyond plain substring matching).
     */
    default boolean isAdvanced() {
        return false;
    }
}
