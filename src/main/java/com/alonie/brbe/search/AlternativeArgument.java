package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * OR composition: any child argument matching suffices.
 * Pipe (|) separates AlternativeArgument groups.
 */
public class AlternativeArgument implements SearchArgument {
    private final List<SearchArgument> children;

    public AlternativeArgument(List<SearchArgument> children) {
        this.children = children;
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        if (children.isEmpty()) {
            return true;
        }
        for (SearchArgument child : children) {
            if (child.matches(stack, cache)) {
                return true;
            }
        }
        return false;
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
