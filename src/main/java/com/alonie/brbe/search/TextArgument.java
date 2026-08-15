package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Plain substring match on the item's hover name.
 * This is equivalent to the original simple search behavior.
 */
public class TextArgument implements SearchArgument {
    private final String searchText;

    public TextArgument(String searchText) {
        this.searchText = searchText.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(searchText);
    }

    @Override
    public boolean isAdvanced() {
        return false;
    }

    public String getSearchText() {
        return searchText;
    }
}
