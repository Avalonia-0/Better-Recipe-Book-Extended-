package com.alonie.recipebookispain_extended.access;

import net.minecraft.world.item.CreativeModeTab;

/**
 * Access interface for RBIP creative tab buttons.
 * Implemented by {@code RecipeBookTabButtonCreativeMixin} on
 * {@code RecipeBookTabButton}.
 */
public interface CreativeTabButtonAccess {
    void rbip$setCreativeTab(CreativeModeTab tab);
    CreativeModeTab rbip$getCreativeTab();
}
