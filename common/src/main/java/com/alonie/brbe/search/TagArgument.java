package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Searches by item tags. Prefix: $
 * <p>
 * Matches if any tag of the item (e.g., "minecraft:logs", "c:stone")
 * contains the query text.
 */
public class TagArgument implements SearchArgument {
    private final String tagQuery;

    public TagArgument(String tagQuery) {
        this.tagQuery = tagQuery.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        for (String tag : cache.getTags(stack)) {
            if (tag.toLowerCase(Locale.ROOT).contains(tagQuery)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}
