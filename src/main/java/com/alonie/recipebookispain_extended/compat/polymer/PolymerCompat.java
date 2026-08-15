package com.alonie.recipebookispain_extended.compat.polymer;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * Polymer-specific compatibility: handles the timing gap between RBIP's
 * initialisation and Polymer's async group sync from the server.
 * <p>
 * The actual namespace-matching logic lives in {@code RecipeBookIsPain}'s
 * core methods ({@code buildNamespaceCache()}, {@code applyNamespaceOverrides()},
 * {@code lookupByNamespace()}). This class only coordinates when those
 * methods are called relative to Polymer's sync cycle.
 */
public final class PolymerCompat {

    private PolymerCompat() {}

    /** Rebuild the namespace cache and check for new groups. */
    public static synchronized void refresh() {
        RecipeBookIsPain.buildNamespaceCache();
        RecipeBookIsPain.applyNamespaceOverrides();
        for (CreativeModeTab group : CreativeModeTabs.allTabs()) {
            RecipeBookIsPain.registerNewGroup(group);
        }
    }
}
