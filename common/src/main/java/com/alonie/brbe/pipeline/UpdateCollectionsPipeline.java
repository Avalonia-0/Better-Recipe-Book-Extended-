package com.alonie.brbe.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Explicit, ordered pipeline that replaces the mixin weave on
 * {@code RecipeBookComponent.updateCollections(boolean)}.
 *
 * <h3>Stage order (fixed)</h3>
 * <ol>
 *   <li>{@code SEARCH_SAVE}  — save/clear search box for advanced queries</li>
 *   <li>{@code CACHE_CHECK}   — check BookStateCache; on hit, restore &amp; skip to PAGE_UPDATE</li>
 *   <li>{@code RBIP_FILTER}   — filter collections by RBIP creative tab</li>
 *   <li>{@code FOR_EACH}      — vanilla forEach (full or incremental)</li>
 *   <li>{@code INCOMPATIBLE}  — mark 3×3 incompatible recipes</li>
 *   <li>{@code PARTIAL_MARK}  — mark partial-material recipes</li>
 *   <li>{@code SEARCH_FILTER} — apply advanced search syntax</li>
 *   <li>{@code PIN_SORT}      — move pinned collections to front</li>
 *   <li>{@code CATEGORY_SORT} — sort: craftable &gt; partial &gt; uncraftable</li>
 *   <li>{@code PAGE_UPDATE}   — call page.updateCollections(finalList, resetPage)</li>
 *   <li>{@code CACHE_SAVE}    — persist result to BookStateCache</li>
 * </ol>
 *
 * <p>After the pipeline runs, the caller should cancel the vanilla method
 * body — all logic has already been executed in the pipeline stages.
 */
public final class UpdateCollectionsPipeline {

    private UpdateCollectionsPipeline() {}

    /**
     * Run the full pipeline.  Called from the single @Inject HEAD on
     * updateCollections.  After this returns, the caller can safely
     * {@code ci.cancel()} — every stage has already run.
     */
    public static void run(RecipeBookComponent component, RecipeBookMenu<?, ?> menu,
                           Minecraft minecraft, List<RecipeCollection> collections,
                           boolean resetPage, Consumer<? super RecipeCollection> vanillaConsumer) {
        PerfTimer.begin();

        // ── Compute immutable inputs ──
        long slotHash = PartialCraftingUtil.slotHash(menu.slots);
        boolean inventoryChanged = (slotHash != brbe$lastSlotHash);
        Object rbipVariant = com.alonie.recipebookispain_extended.RecipeBookIsPain.activeCreativeTab;

        PipelineContext ctx = new PipelineContext(
                collections, menu, minecraft, resetPage,
                slotHash, inventoryChanged, menu.getClass(), rbipVariant
        );

        // ── Stage 1: SEARCH_SAVE ──
        searchSave(ctx, component);

        // ── Stage 2: CACHE_CHECK ──
        boolean continuePipeline = cacheCheck(ctx, component);
        if (continuePipeline) {
            // ── Stage 3: RBIP_FILTER ──
            List<RecipeCollection> filtered = rbipFilter(ctx, component);
            if (filtered != null) {
                ctx.workingList.clear();
                ctx.workingList.addAll(filtered);
            }

            // ── Stage 4: FOR_EACH ──
            forEach(ctx, vanillaConsumer);

            // ── Stage 5: INCOMPATIBLE ──
            incompatibleMark(ctx);

            // ── Stage 6: PARTIAL_MARK ──
            partialMark(ctx);

            // ── Stage 7: SEARCH_FILTER ──
            searchFilter(ctx, component);

            // ── Stage 8: PIN_SORT ──
            pinSort(ctx);

            // ── Stage 9: CATEGORY_SORT ──
            categorySort(ctx);
        }

        // ── Stage 10: PAGE_UPDATE (always runs) ──
        pageUpdate(ctx, component);

        // ── Stage 11: CACHE_SAVE (always runs) ──
        cacheSave(ctx, component);

        // ── Restore search text (always) ──
        searchRestore(ctx, component);

        brbe$lastSlotHash = ctx.cacheWasHit ? brbe$lastSlotHash : ctx.slotHash;
    }

    // ── Holds lastSlotHash (was a @Unique static field on the mixin) ──
    static long brbe$lastSlotHash;

    // ═══════════════════════════════════════════════════════════════
    // Stage implementations
    // ═══════════════════════════════════════════════════════════════

    private static void searchSave(PipelineContext ctx, RecipeBookComponent component) {
        var accessor = (RecipeBookComponentAccessor) component;
        var searchBox = accessor.getSearchBox();
        if (searchBox == null) return;
        ctx.searchBoxValue = searchBox.getValue();
        if (ctx.searchBoxValue == null || ctx.searchBoxValue.isEmpty()) return;

        SearchQuery query = SearchQuery.parse(ctx.searchBoxValue);
        if (query.isAdvanced()) {
            ctx.savedSearchText = ctx.searchBoxValue;
            ctx.parsedQuery = query;
            searchBox.setValue("");
        }
    }

    private static boolean cacheCheck(PipelineContext ctx, RecipeBookComponent component) {
        if (ctx.minecraft.screen == null) return true;

        Class<?> screenClass = ctx.minecraft.screen.getClass();
        List<RecipeCollection> cached = BookStateCache.get(screenClass, ctx.slotHash, ctx.rbipVariant);

        if (cached != null) {
            BetterRecipeBook.LOGGER.info("[BRBE-Pipeline] CACHE-HIT {} (hash={}, {} colls)",
                    screenClass.getSimpleName(), ctx.slotHash, cached.size());
            ctx.cachedPageList = cached;
            ctx.cacheWasHit = true;
            return false; // skip data stages
        }
        return true; // continue pipeline
    }

    private static List<RecipeCollection> rbipFilter(PipelineContext ctx, RecipeBookComponent component) {
        // Mirror the original RBIP @Redirect guard: only filter when
        // a creative tab is active AND the category is UNKNOWN.
        // Vanilla tabs (CRAFTING_SEARCH etc.) should NOT be filtered
        // even if activeCreativeTab is still set from a previous click.
        if (ctx.rbipVariant == null) return null;

        var accessor = (RecipeBookComponentAccessor) component;
        var selectedTab = accessor.getSelectedTab();
        if (selectedTab == null || selectedTab.getCategory() != net.minecraft.client.RecipeBookCategories.UNKNOWN) {
            return null; // vanilla tab active — don't filter
        }

        var rbipTab = (net.minecraft.world.item.CreativeModeTab) ctx.rbipVariant;

        try {
            var player = ctx.minecraft.player;
            if (player == null) return null;
            var book = player.getRecipeBook();

            // Determine category (mirrors RBIP's furnace detection)
            net.minecraft.client.RecipeBookCategories searchCategory;
            if (ctx.menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu fm) {
                if (fm instanceof net.minecraft.world.inventory.SmokerMenu) {
                    searchCategory = net.minecraft.client.RecipeBookCategories.SMOKER_SEARCH;
                } else if (fm instanceof net.minecraft.world.inventory.BlastFurnaceMenu) {
                    searchCategory = net.minecraft.client.RecipeBookCategories.BLAST_FURNACE_SEARCH;
                } else {
                    searchCategory = net.minecraft.client.RecipeBookCategories.FURNACE_SEARCH;
                }
            } else {
                searchCategory = net.minecraft.client.RecipeBookCategories.CRAFTING_SEARCH;
            }

            List<RecipeCollection> allBase = book.getCollection(searchCategory);
            var level = ctx.minecraft.level;
            if (level == null) return null;

            List<RecipeCollection> matching = new ArrayList<>();
            for (RecipeCollection coll : allBase) {
                for (RecipeHolder<?> holder : coll.getRecipes()) {
                    var result = holder.value().getResultItem(level.registryAccess());
                    if (!result.isEmpty()
                            && com.alonie.recipebookispain_extended.RecipeBookIsPain.isItemInTab(result, rbipTab)) {
                        matching.add(coll);
                        break;
                    }
                }
            }
            return matching;
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-Pipeline] RBIP filter failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void forEach(PipelineContext ctx, Consumer<? super RecipeCollection> vanillaConsumer) {
        if (!ctx.inventoryChanged) {
            // Inventory unchanged — craftable data from previous call is still fresh.
            return;
        }

        // Try incremental path
        Set<Item> changedItems = SlotTracker.changedItems(ctx.menuClass, ctx.menu.slots);
        boolean incremental = false;

        if (changedItems != null && RecipeIndex.isBuilt()) {
            Set<RecipeCollection> dirty = RecipeIndex.getAffected(changedItems);
            if (dirty.size() < ctx.workingList.size() / 2) {
                incremental = true;
                ctx.dirtySet = dirty;
                PerfTimer.start("vanilla.forEach-incr");
                int processed = 0;
                for (RecipeCollection coll : ctx.workingList) {
                    if (dirty.contains(coll)) {
                        PartialCraftingUtil.clearCategory(coll);
                        ((Consumer<RecipeCollection>) vanillaConsumer).accept(coll);
                        processed++;
                    }
                }
                PerfTimer.end("vanilla.forEach-incr");
                BetterRecipeBook.LOGGER.info("[BRBE-Pipeline] INCR dirty={}/{} items={}",
                        processed, ctx.workingList.size(), changedItems.size());
            }
        }

        if (!incremental) {
            PerfTimer.start("vanilla.forEach");
            ctx.workingList.forEach((Consumer<RecipeCollection>) vanillaConsumer);
            PerfTimer.end("vanilla.forEach");

            // Build the reverse index on first full run
            if (!RecipeIndex.isBuilt() && ctx.minecraft.player != null) {
                RecipeIndex.build(ctx.minecraft.player.getRecipeBook().getCollections());
            }
        }
    }

    private static void incompatibleMark(PipelineContext ctx) {
        boolean onInventory = ctx.minecraft.screen instanceof InventoryScreen;
        boolean retainIncompatible = onInventory
                && BetterRecipeBook.config.showAllRecipesInSurvival;

        if (!retainIncompatible || !ctx.inventoryChanged) return;

        PerfTimer.start("incompatible.mark");
        Iterable<RecipeCollection> targets = ctx.dirtySet != null
                ? (Iterable<RecipeCollection>) ctx.dirtySet : ctx.workingList;
        for (RecipeCollection coll : targets) {
            IncompatibleCraftingUtil.markIncompatibleRecipes(coll);
        }
        PerfTimer.end("incompatible.mark");
    }

    private static void partialMark(PipelineContext ctx) {
        if (!BetterRecipeBook.config.partialMarkingEnabled) return;
        if (!ctx.inventoryChanged) {
            PerfTimer.logAndReset("updateCollections (cache-hit, fully skipped)");
            return;
        }

        PartialCraftingUtil.beginFilteringUpdate(true);
        try {
            Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(ctx.menu.slots);
            Iterable<RecipeCollection> targets = ctx.dirtySet != null
                    ? (Iterable<RecipeCollection>) ctx.dirtySet : ctx.workingList;

            PerfTimer.start("partial.step0-clear");
            for (RecipeCollection coll : targets) {
                if (PartialCraftingUtil.hasPartialMaterials(coll)) {
                    RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) coll;
                    for (RecipeHolder<?> holder : coll.getRecipes()) {
                        if (PartialCraftingUtil.isPartiallyCraftable(coll, holder.id())) {
                            accessor.betterRecipeBook$getCraftable().remove(holder);
                        }
                    }
                }
            }
            PerfTimer.end("partial.step0-clear");

            PerfTimer.start("partial.markAndInject");
            int collCount = 0;
            for (RecipeCollection coll : targets) {
                collCount++;
                PartialCraftingUtil.markPartialMaterials(coll, inventoryItems);
                if (PartialCraftingUtil.hasPartialMaterials(coll)) {
                    RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) coll;
                    for (RecipeHolder<?> holder : coll.getRecipes()) {
                        if (PartialCraftingUtil.isPartiallyCraftable(coll, holder.id())) {
                            accessor.betterRecipeBook$getCraftable().add(holder);
                        }
                    }
                }
            }
            PerfTimer.end("partial.markAndInject");
            PerfTimer.logAndReset("updateCollections ("
                    + (ctx.dirtySet != null ? "incr:" : "") + collCount + " coll)");
        } finally {
            PartialCraftingUtil.beginFilteringUpdate(false);
        }
    }

    private static void searchFilter(PipelineContext ctx, RecipeBookComponent component) {
        if (ctx.parsedQuery == null) return;
        var query = (SearchQuery) ctx.parsedQuery;
        var level = ctx.minecraft.level;
        if (level == null) return;

        SearchCache cache = new SearchCache();
        var registryAccess = level.registryAccess();
        List<RecipeCollection> filtered = new ArrayList<>();

        for (RecipeCollection coll : ctx.workingList) {
            for (RecipeHolder<?> recipe : coll.getRecipes()) {
                var result = recipe.value().getResultItem(registryAccess);
                if (result != null && !result.isEmpty() && query.matches(result, cache)) {
                    filtered.add(coll);
                    break;
                }
            }
        }
        ctx.workingList.clear();
        ctx.workingList.addAll(filtered);
    }

    private static void pinSort(PipelineContext ctx) {
        if (!BetterRecipeBook.config.enablePinning) return;
        PerfTimer.start("pinSort");
        List<RecipeCollection> temp = new ArrayList<>(ctx.workingList);
        for (RecipeCollection coll : temp) {
            if (BetterRecipeBook.pinnedRecipeManager.has(
                    com.alonie.brbe.generic.pins.PinnableRecipeCollection.of(coll))) {
                ctx.workingList.remove(coll);
                ctx.workingList.add(0, coll);
            }
        }
        PerfTimer.end("pinSort");
    }

    private static void categorySort(PipelineContext ctx) {
        boolean filtering = ctx.minecraft.player != null
                && ctx.minecraft.player.getRecipeBook().isFiltering(ctx.menu);
        boolean shouldSort = BetterRecipeBook.config.partialCraftingEnabled
                || (BetterRecipeBook.config.partialMarkingEnabled && filtering);

        if (!shouldSort) return;

        PerfTimer.start("sort");
        boolean useFullSort = BetterRecipeBook.config.partialCraftingEnabled || filtering;
        boolean hasPartialData = BetterRecipeBook.config.partialMarkingEnabled;

        List<RecipeCollection> front = new ArrayList<>();
        List<RecipeCollection> middle = new ArrayList<>();
        List<RecipeCollection> back = new ArrayList<>();

        for (RecipeCollection c : ctx.workingList) {
            if (hasPartialData) {
                CollectionCategory cat = PartialCraftingUtil.categorize(c);
                if (useFullSort) {
                    switch (cat) {
                        case TRULY_CRAFTABLE -> front.add(c);
                        case PARTIAL -> middle.add(c);
                        case UNASSIGNED -> back.add(c);
                    }
                } else {
                    if (cat != CollectionCategory.UNASSIGNED) front.add(c);
                    else back.add(c);
                }
            } else {
                if (c.hasCraftable()) front.add(c);
                else back.add(c);
            }
        }

        ctx.workingList.clear();
        ctx.workingList.addAll(front);
        ctx.workingList.addAll(middle);
        ctx.workingList.addAll(back);
        PerfTimer.end("sort");

        ctx.finalPageList = ctx.workingList;
    }

    private static void pageUpdate(PipelineContext ctx, RecipeBookComponent component) {
        List<RecipeCollection> list = ctx.cacheWasHit ? ctx.cachedPageList : ctx.workingList;
        if (ctx.finalPageList == null && !ctx.cacheWasHit) {
            ctx.finalPageList = list;
        }
        RecipeBookPage page = ((RecipeBookComponentAccessor) component).getRecipeBookPage();
        page.updateCollections(list, ctx.resetPage);
    }

    private static void cacheSave(PipelineContext ctx, RecipeBookComponent component) {
        if (ctx.finalPageList == null || ctx.finalPageList.isEmpty()) return;
        if (ctx.minecraft.screen == null) return;

        Class<?> screenClass = ctx.minecraft.screen.getClass();
        Object variant = com.alonie.recipebookispain_extended.RecipeBookIsPain.activeCreativeTab;
        BetterRecipeBook.LOGGER.info("[BRBE-Pipeline] SAVE {} (hash={}, variant={}, {} colls)",
                screenClass.getSimpleName(), ctx.slotHash,
                variant != null ? "rbip" : "none", ctx.finalPageList.size());
        BookStateCache.put(screenClass, ctx.slotHash, ctx.finalPageList, variant);
    }

    private static void searchRestore(PipelineContext ctx, RecipeBookComponent component) {
        if (ctx.savedSearchText != null) {
            var searchBox = ((RecipeBookComponentAccessor) component).getSearchBox();
            if (searchBox != null) {
                searchBox.setValue(ctx.savedSearchText);
            }
        }
    }
}
