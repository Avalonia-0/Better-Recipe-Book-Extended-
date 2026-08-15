package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

/**
 * Negation: inverts the child argument's result.
 * Prefix: - (e.g., -@minecraft, -$logs)
 */
public class NegatedArgument implements SearchArgument {
    private final SearchArgument child;

    public NegatedArgument(SearchArgument child) {
        this.child = child;
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        return !child.matches(stack, cache);
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}
