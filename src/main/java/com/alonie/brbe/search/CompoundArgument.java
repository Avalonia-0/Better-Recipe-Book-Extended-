package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * AND composition: all child arguments must match.
 * Space-separated tokens within an OR group form a CompoundArgument.
 */
public class CompoundArgument implements SearchArgument {
    private final List<SearchArgument> children;

    public CompoundArgument(List<SearchArgument> children) {
        this.children = children;
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        for (SearchArgument child : children) {
            if (!child.matches(stack, cache)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isAdvanced() {
        for (SearchArgument child : children) {
            if (child.isAdvanced()) {
                return true;
            }
        }
        return false;
    }
}
