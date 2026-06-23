package com.alonie.brbe.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;

import java.util.List;
import java.util.Set;

/**
 * Mutable context passed through each stage of the updateCollections pipeline.
 *
 * <p>Stages read and modify the fields they care about.  The pipeline
 * guarantees stages execute in a fixed, documented order.
 */
public class PipelineContext {

    // ── Input (set before pipeline runs) ──
    public final List<RecipeCollection> collections;
    public final RecipeBookMenu<?, ?> menu;
    public final Minecraft minecraft;
    public final boolean resetPage;
    public final long slotHash;
    public final boolean inventoryChanged;
    public final Class<?> menuClass;
    public final Object rbipVariant; // activeCreativeTab or null

    // ── Output / stage state ──
    public RecipeBookPage page;
    /** Set by CacheCheckStage on hit — the cached page list to restore. */
    public List<RecipeCollection> cachedPageList;
    /** After CacheCheckStage: true if a cached result was restored. */
    public boolean cacheWasHit;
    /** Set by incremental detection — if non-null, only these collections need re-evaluation. */
    public Set<RecipeCollection> dirtySet;
    /** Final list passed to page.updateCollections (set by sort stage). */
    public List<RecipeCollection> finalPageList;

    // ── Search box state (cross-stage) ──
    public String savedSearchText;
    public Object parsedQuery; // SearchQuery (avoiding compile dependency)
    public String searchBoxValue;

    PipelineContext(List<RecipeCollection> collections, RecipeBookMenu<?, ?> menu,
                    Minecraft minecraft, boolean resetPage, long slotHash,
                    boolean inventoryChanged, Class<?> menuClass, Object rbipVariant) {
        this.collections = collections;
        this.menu = menu;
        this.minecraft = minecraft;
        this.resetPage = resetPage;
        this.slotHash = slotHash;
        this.inventoryChanged = inventoryChanged;
        this.menuClass = menuClass;
        this.rbipVariant = rbipVariant;
    }
}
