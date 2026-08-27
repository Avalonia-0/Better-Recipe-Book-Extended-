package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic pipeline for transforming the recipe collection list
 * before it is passed to {@code RecipeBookPage.updateCollections()}.
 *
 * <h3>Pipeline order (this is the single source of truth)</h3>
 * <ol>
 *   <li><b>Search filter</b> — remove collections that don't match an
 *       advanced search query.  Runs first to reduce the working set.</li>
 *   <li><b>Ungroup split</b> — split multi-recipe collections into
 *       single-recipe collections (when {@code noGrouped} is on).</li>
 *   <li><b>Pins sort</b> — move pinned collections to the front of the
 *       list.  Runs after ungroup so it sees the final collection objects.</li>
 *   <li><b>Partial sort</b> — sort craftable before partially-craftable
 *       before uncraftable.</li>
 * </ol>
 *
 * <p>Each stage is a pure function (or mutates in-place where documented),
 * making the pipeline testable without the Mixin framework.
 */
public final class CollectionPipeline {

    private CollectionPipeline() {}

    // ---- Stage 1: Advanced search filter ----

    /**
     * Filters the list to only collections whose result items match
     * the advanced search query.  Returns a new list.
     */
    public static List<RecipeCollection> applySearch(
            List<RecipeCollection> collections,
            SearchQuery query,
            ContextMap displayContext) {
        if (query == null || displayContext == null) {
            return collections;
        }

        SearchCache cache = new SearchCache();
        List<RecipeCollection> filtered = new ArrayList<>();

        for (RecipeCollection collection : collections) {
            boolean added = false;
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                List<ItemStack> results = entry.resultItems(displayContext);
                for (ItemStack result : results) {
                    if (result != null && !result.isEmpty()
                            && query.matches(result, cache)) {
                        filtered.add(collection);
                        added = true;
                        break;
                    }
                }
                if (added) break;
            }
        }

        return filtered;
    }

    // ---- Stage 2: Ungroup split ----

    /**
     * Splits multi-recipe collections into single-recipe collections when
     * {@code alternativeRecipes.noGrouped} is enabled.  Returns a new list.
     */
    public static List<RecipeCollection> applyUngroup(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.config.alternativeRecipes.noGrouped) {
            return collections;
        }

        List<RecipeCollection> split = new ArrayList<>(collections.size());
        for (RecipeCollection collection : collections) {
            List<RecipeDisplayEntry> recipes = collection.getRecipes();
            if (recipes.size() <= 1) {
                split.add(collection);
                continue;
            }

            RecipeCollectionAccessor source = (RecipeCollectionAccessor) collection;
            // EvenIfStale：分代感知的 hasPartialMaterials 在分代推进后未重新标记时会
            // 返回 false，导致部分可合成集合被错误过滤（管线可能在标记之前运行）。
            boolean restrictToCraftableOrPartial = PartialCraftingUtil.hasPartialMaterialsEvenIfStale(collection)
                    || collection.hasCraftable();
            boolean addedAny = false;

            for (RecipeDisplayEntry recipe : recipes) {
                if (!source.brbe$getSelected().contains(recipe.id())) {
                    continue;
                }

                boolean isCraftable = source.brbe$getCraftable().contains(recipe.id());
                boolean isPartial = PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, recipe.id());
                if (restrictToCraftableOrPartial && !isCraftable && !isPartial) {
                    continue;
                }

                RecipeCollection child = new RecipeCollection(Collections.singletonList(recipe));
                RecipeCollectionAccessor childAccessor = (RecipeCollectionAccessor) child;
                childAccessor.brbe$getSelected().add(recipe.id());
                if (isCraftable) {
                    childAccessor.brbe$getCraftable().add(recipe.id());
                }
                if (isPartial) {
                    PartialCraftingUtil.markPartialMaterial(child, recipe.id());
                }

                split.add(child);
                addedAny = true;
            }

            if (!addedAny && !restrictToCraftableOrPartial) {
                split.add(collection);
            }
        }

        return split;
    }

    // ---- Stage 3: Pins sort (in-place) ----

    /**
     * Moves <b>fully-pinned</b> collections (every recipe pinned — standalone
     * pin recipes and whole pin groups) to the front of the list.  Mutates
     * the list in place (removes and re-inserts at index 0).
     *
     * <p>Partially-pinned groups are <b>not</b> moved: per the alternative-group
     * rule, pinning a single variant must not reorder the original group — its
     * pinned variant is extracted to the front by {@link #applyPinCopyGroups}
     * (Stage 6) instead.
     */
    public static void applyPins(List<RecipeCollection> collections) {
        // Iterate a snapshot to avoid ConcurrentModificationException
        List<RecipeCollection> snapshot = new ArrayList<>(collections);
        for (RecipeCollection collection : snapshot) {
            if (BetterRecipeBook.pinnedRecipeManager.isFullyPinned(
                    PinnableRecipeCollection.of(collection))) {
                collections.remove(collection);
                collections.add(0, collection);
            }
        }
    }

    // ---- Stage 4: Partial sort ----

    /**
     * Sorts collections with pinned recipes always at highest priority,
     * then fully-craftable, then partially-craftable, then uncraftable.
     *
     * <p>Pin priority is absolute: a pinned uncraftable recipe comes
     * before an unpinned craftable one.  Within each pin group, the
     * standard category ordering applies.
     */
    public static List<RecipeCollection> applyPartialSort(
            List<RecipeCollection> collections,
            boolean useFullSort,
            boolean hasPartialData) {

        // Phase 1: split by pin status × category
        List<RecipeCollection> pinnedCraftable = new ArrayList<>();
        List<RecipeCollection> pinnedPartial = new ArrayList<>();
        List<RecipeCollection> pinnedUncraftable = new ArrayList<>();
        List<RecipeCollection> unpinnedCraftable = new ArrayList<>();
        List<RecipeCollection> unpinnedPartial = new ArrayList<>();
        List<RecipeCollection> unpinnedUncraftable = new ArrayList<>();

        for (RecipeCollection c : collections) {
            // 局部 pin 的原组按"未 pin"参与排序（pin 变体由 Stage 6 剥离置顶，
            // 原组排序不受影响）
            boolean isPinned = BetterRecipeBook.pinnedRecipeManager.isFullyPinned(
                        PinnableRecipeCollection.of(c));

            if (hasPartialData) {
                // Use EvenIfStale: generation-aware queries can return false
                // when the generation was incremented without re-marking (e.g.
                // inventory unchanged between tab switches). EvenIfStale
                // guarantees consistent sorting regardless of generation state.
                CollectionCategory cat = categorizeEvenIfStale(c);
                if (isPinned) {
                    switch (cat) {
                        case TRULY_CRAFTABLE -> pinnedCraftable.add(c);
                        case PARTIAL -> pinnedPartial.add(c);
                        case UNASSIGNED -> pinnedUncraftable.add(c);
                    }
                } else {
                    switch (cat) {
                        case TRULY_CRAFTABLE -> unpinnedCraftable.add(c);
                        case PARTIAL -> unpinnedPartial.add(c);
                        case UNASSIGNED -> unpinnedUncraftable.add(c);
                    }
                }
            } else {
                if (c.hasCraftable()) {
                    if (isPinned) pinnedCraftable.add(c);
                    else unpinnedCraftable.add(c);
                } else {
                    if (isPinned) pinnedUncraftable.add(c);
                    else unpinnedUncraftable.add(c);
                }
            }
        }

        // Phase 2: pinned before unpinned in each category
        List<RecipeCollection> result = new ArrayList<>(collections.size());
        result.addAll(pinnedCraftable);
        result.addAll(pinnedPartial);
        result.addAll(pinnedUncraftable);
        result.addAll(unpinnedCraftable);
        result.addAll(unpinnedPartial);
        result.addAll(unpinnedUncraftable);
        return result;
    }

    /**
     * Like {@link PartialCraftingUtil#categorize} but uses EvenIfStale
     * queries.  Safe to call regardless of generation state — guarantees
     * consistent sorting even when the tagger generation was incremented
     * without re-marking collections.
     *
     * <p>Result is cached per collection identity, keyed by the recipe
     * crafting index generation.  When the inventory is unchanged
     * ({@link RecipeCraftingIndex#inventoryUnchanged()}) a collection's
     * craftable/partial state is identical to the last pass, so the category
     * is stable — the cache turns the O(recipes-per-collection) evaluation
     * into an O(1) map lookup.  The cache is naturally invalidated on
     * collection rebuild (generation bump) and on inventory change
     * ({@link RecipeCraftingIndex#inventoryChangedVersion}).
     */
    private static final java.util.Map<RecipeCollection, CachedCategory> CATEGORY_CACHE =
            new java.util.WeakHashMap<>();

    private static int lastCategoryCacheVersion = Integer.MIN_VALUE;

    private record CachedCategory(CollectionCategory category, int version) {}

    private static CollectionCategory categorizeEvenIfStale(RecipeCollection c) {
        int version = RecipeCraftingIndex.currentVersion();
        if (version != lastCategoryCacheVersion) {
            CATEGORY_CACHE.clear();
            lastCategoryCacheVersion = version;
        }
        CachedCategory cached = CATEGORY_CACHE.get(c);
        if (cached != null && cached.version() == version) {
            return cached.category();
        }

        boolean truly = false, partial = false;
        for (RecipeDisplayEntry entry : c.getRecipes()) {
            if (PartialCraftingUtil.isPartiallyCraftableEvenIfStale(c, entry.id())) {
                partial = true;
            } else if (c.isCraftable(entry.id())) {
                truly = true;
            }
        }
        CollectionCategory category;
        if (truly) category = CollectionCategory.TRULY_CRAFTABLE;
        else if (partial) category = CollectionCategory.PARTIAL;
        else category = CollectionCategory.UNASSIGNED;

        CATEGORY_CACHE.put(c, new CachedCategory(category, version));
        return category;
    }

    // ---- Stage 5: Filter toggle ----

    /**
     * Removes collections that have no craftable (and, when partial marking
     * is enabled, no partially-craftable) recipes.  Returns a new list.
     * When the filter toggle is off, returns the original list unchanged.
     */
    public static List<RecipeCollection> applyFilterToggle(
            List<RecipeCollection> collections,
            boolean isFiltering) {
        if (!isFiltering) return collections;

        boolean hasPartial = BetterRecipeBook.config.partialMarkingEnabled;
        List<RecipeCollection> result = new ArrayList<>();
        for (RecipeCollection coll : collections) {
            // Use EvenIfStale: generation-aware hasPartialMaterials can
            // return false after a generation bump without re-marking,
            // causing partial collections to be incorrectly filtered out.
            boolean keep = hasPartial
                    ? coll.hasCraftable() || PartialCraftingUtil.hasPartialMaterialsEvenIfStale(coll)
                    : coll.hasCraftable();
            if (keep) result.add(coll);
        }
        return result;
    }

    // ---- Stage 6: Pin extraction ----

    /** 本阶段生成的重打包组身份（弱集合：随列表重建 GC，不造成残留）。 */
    private static final java.util.Set<RecipeCollection> PIN_COPIES =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /**
     * pin 剥离 stage（管线末端调用）——用户规则：pin 的变体从原组**剥离**，
     * 原组（重打包）只保留未 pin 变体且**位置不变**（pin 单个变体不得使原组
     * 重新排序）；pin 集合的展示**置顶**（与"pin 置顶"规则一致）：
     * <ul>
     *   <li><b>1 个 pin</b>：生成**独立单配方组**（该变体单独成组，带 pin 贴图）
     *      排在列表最前；</li>
     *   <li><b>≥2 个 pin</b>：生成**副本替代配方组**（只含这些 pin 配方，全 pin
     *      → 贴图判定自然命中）排在列表最前；多个原组的 pin 组保持原组顺序；</li>
     *   <li><b>全 pin</b>：原组不再重打包（它就是 pin 组形态，直接保留贴图）；</li>
     *   <li>取消 pin 后变体回归原组（下次管线重算自动还原）。</li>
     * </ul>
     * 幂等：上一轮生成的重打包组先从列表移除再重新生成。
     */
    public static void applyPinCopyGroups(List<RecipeCollection> collections) {
        if (collections == null || collections.isEmpty()) return;

        // 1) 移除上一轮生成的重打包组（管线缓存列表可能已带有）
        List<RecipeCollection> stale = new ArrayList<>();
        for (RecipeCollection c : collections) {
            if (PIN_COPIES.contains(c)) stale.add(c);
        }
        if (!stale.isEmpty()) {
            collections.removeAll(stale);
            PIN_COPIES.removeAll(stale);
        }

        // 2) 逐组剥离：原组 → [重打包 rest 组（未 pin 变体，原位）] + [pin 组（置顶）]
        java.util.Map<RecipeCollection, RecipeCollection> restPacks =
                new java.util.LinkedHashMap<>();
        java.util.List<RecipeCollection> pinPacks = new ArrayList<>();
        for (RecipeCollection collection : collections) {
            List<RecipeDisplayEntry> pinned = new ArrayList<>();
            List<RecipeDisplayEntry> rest = new ArrayList<>();
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (entry != null && BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(entry)) {
                    pinned.add(entry);
                } else if (entry != null) {
                    rest.add(entry);
                }
            }
            if (pinned.isEmpty()) continue;
            if (pinned.size() == collection.getRecipes().size()) {
                // 全 pin：原组本身就是 pin 组形态（保留，贴图由 isFullyPinned 命中）
                continue;
            }
            RecipeCollection restPack = buildPack(rest, collection);
            RecipeCollection pinPack = buildPack(pinned, collection);
            PIN_COPIES.add(restPack);
            PIN_COPIES.add(pinPack);
            restPacks.put(collection, restPack);
            pinPacks.add(pinPack);
            BetterRecipeBook.LOGGER.info(
                    "[BRBE-PINS] pin-extract: {} recipes, {} pinned variants -> rest {} + pin-group {}",
                    collection.getRecipes().size(), pinned.size(), rest.size(), pinned.size());
        }
        if (restPacks.isEmpty()) return;

        // 3) 原位替换：原组位置只保留 rest 组（未 pin 变体；排序不受影响）
        for (java.util.Map.Entry<RecipeCollection, RecipeCollection> e : restPacks.entrySet()) {
            int idx = collections.indexOf(e.getKey());
            if (idx < 0) continue;
            collections.remove(e.getKey());
            collections.add(idx, e.getValue());
        }

        // 4) pin 组置顶（按原组遍历顺序，与 pin 置顶规则一致）
        if (!pinPacks.isEmpty()) {
            collections.addAll(0, pinPacks);
        }
    }

    /** 由一组变体构建重打包组（同原组语义：全选中，craftable 按玩家物品栏）。 */
    private static RecipeCollection buildPack(List<RecipeDisplayEntry> entries,
                                              RecipeCollection template) {
        RecipeCollection pack = new RecipeCollection(new ArrayList<>(entries));
        StackedItemContents stacked = new StackedItemContents();
        PartialCraftingUtil.fillSearchSpaceStackedContents(stacked);
        pack.selectRecipes(stacked, display -> true);
        return pack;
    }
}
