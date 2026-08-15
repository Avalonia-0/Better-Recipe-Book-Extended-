package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Searches by mod name or namespace. Prefix: @
 * <p>
 * Matches if the item's mod namespace (e.g., "minecraft") or the
 * mod's display name (e.g., "Minecraft") contains the query text.
 */
public class ModArgument implements SearchArgument {
    private final String modQuery;

    public ModArgument(String modQuery) {
        this.modQuery = modQuery.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        String namespace = cache.getModNamespace(stack);
        if (namespace.toLowerCase(Locale.ROOT).contains(modQuery)) {
            return true;
        }
        String modName = cache.getModName(stack);
        return modName.toLowerCase(Locale.ROOT).contains(modQuery);
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}
