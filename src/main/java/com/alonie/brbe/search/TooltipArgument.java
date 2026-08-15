package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Searches by tooltip text. Prefix: #
 * <p>
 * Matches if the concatenated tooltip lines of the item contain the query text.
 */
public class TooltipArgument implements SearchArgument {
    private final String tooltipQuery;

    public TooltipArgument(String tooltipQuery) {
        this.tooltipQuery = tooltipQuery.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        String tooltip = cache.getTooltipText(stack);
        return tooltip != null && tooltip.toLowerCase(Locale.ROOT).contains(tooltipQuery);
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}
