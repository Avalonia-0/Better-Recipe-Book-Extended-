package com.alonie.brbe.mixins.accessors;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {

    /**
     * Invalidate the cached tab-build parameters.
     *
     * RBIP eagerly calls {@code CreativeModeTabs.tryRebuildTabContents} to
     * populate creative-tab contents before the recipe book is built.  This
     * caches {@code CACHED_PARAMETERS}, so when the player later opens the
     * creative inventory screen the game sees "no rebuild needed" and skips
     * {@code SessionSearchTrees.updateCreativeTooltips/updateCreativeTags} —
     * leaving the creative search trees permanently {@code SearchTree.empty()}
     * and every search blank.  Nulling the cache forces the game to rebuild
     * on the creative screen and build the search trees.
     */
    @Accessor("CACHED_PARAMETERS")
    static void brbe$invalidateCachedParameters(CreativeModeTab.ItemDisplayParameters params) {}
}
