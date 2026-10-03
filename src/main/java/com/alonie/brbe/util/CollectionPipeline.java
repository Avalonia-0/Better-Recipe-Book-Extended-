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
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

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
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (entryMatches(entry, query, displayContext, cache)) {
                    filtered.add(collection);
                    break;
                }
            }
        }

        return filtered;
    }

    /**
     * 单个变体是否命中搜索（**唯一**判据：{@link #applySearch} 与
     * {@link #applySortExtraction} 共用，避免"整组被保留、组内却没有命中的变体"这种
     * 两边判据漂移导致整组消失）。
     */
    public static boolean entryMatches(RecipeDisplayEntry entry,
                                       SearchQuery query,
                                       ContextMap displayContext,
                                       SearchCache cache) {
        if (entry == null || query == null || displayContext == null) {
            return true;
        }
        List<ItemStack> results = entry.resultItems(displayContext);
        for (ItemStack result : results) {
            if (result != null && !result.isEmpty() && query.matches(result, cache)) {
                return true;
            }
        }
        return false;
    }

    // ---- Stage 2: Ungroup split ----

    /**
     * Splits multi-recipe collections into single-recipe collections when
     * {@code alternativeRecipes.noGrouped()} is enabled.  Returns a new list.
     */
    public static List<RecipeCollection> applyUngroup(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.config.alternativeRecipes.noGrouped()) {
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

    private record CachedCategory(CollectionCategory category, int version, int stateHash) {}

    private static CollectionCategory categorizeEvenIfStale(RecipeCollection c) {
        int version = RecipeCraftingIndex.currentVersion();
        // ⚠️ 只按 currentVersion 失效是**不够的**（2026-09-22 用户反馈："配方状态变了，
        // 整体排序不立即刷新"）。分类的实际输入是「集合的 craftable 集合 + 残缺标记」，
        // 而这两者都会在**库存数量没变**时改变：
        //   · 配置整轮重标记（partialMarkingEnabled 开关 / invalidateCaches）
        //   · 手持（carried）或副手变化触发的重标记 —— BRBE 的 slotHash 计入二者，
        //     但 vanilla 的 stackedContents（= RecipeCraftingIndex 的 diff 源）不含副手、
        //     也不含 carried → currentVersion 不变
        //   · pin 浮层的 forceReevaluate / carried 提升注入 craftable
        // 于是分类一直是过期的：按钮状态（读 craftable 集合）已经变了，Stage 4 排出来的
        // 顺序却还把它放在旧桶里 —— 症状正是"状态变了、排序不刷新"。
        // 这里把**集合自身的状态哈希**（与管线指纹同一套分量：craftable + selected +
        // 残缺标记，见 PartialCraftingUtil#pipelineStateHash）也纳入命中判据。
        int stateHash = PartialCraftingUtil.pipelineStateHash(java.util.List.of(c));
        if (version != lastCategoryCacheVersion) {
            CATEGORY_CACHE.clear();
            lastCategoryCacheVersion = version;
        }
        CachedCategory cached = CATEGORY_CACHE.get(c);
        if (cached != null && cached.version() == version && cached.stateHash() == stateHash) {
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

        CATEGORY_CACHE.put(c, new CachedCategory(category, version, stateHash));
        return category;
    }

    // ⚠️ 曾经的 `cellSortCategory`（"专用收纳格整格取最高类别、格内不拆"）已按用户 2026-09-30
    //    指令**撤回**：收纳格必须同样遵循 2026-09-27 定稿的「按排序原因智能拆分」——组内有变体
    //    状态改变（pin / 可合成 / 残缺）时，该变体照常被剥出去参与排序，格内只留基线变体。
    //    见 applySortExtraction 的 javadoc。

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

    // ---- Stage 2.6: 同产物合并（专用收纳格）----

    /**
     * Stage 2.6 的产物：新列表 + 两份额外状态（按**集合身份**，供 Stage 2.5 的去重与搜索去重用）。
     *
     * <p>{@code dedicated}：专用收纳格（含它们的剥离子组血统）；{@code copied}：混合组 →
     * 该组里"已复制进收纳格"的条目 id。生命周期 = 单次管线调用（由调用点持有）。</p>
     */
    public static final class MergeResult {
        private final List<RecipeCollection> list;
        private final java.util.Set<RecipeCollection> dedicated;
        private final java.util.Map<RecipeCollection, java.util.Set<RecipeDisplayId>> copied;

        private MergeResult(List<RecipeCollection> list,
                            java.util.Set<RecipeCollection> dedicated,
                            java.util.Map<RecipeCollection, java.util.Set<RecipeDisplayId>> copied) {
            this.list = list;
            this.dedicated = dedicated;
            this.copied = copied;
        }

        /** 合并后的列表。 */
        public List<RecipeCollection> list() {
            return this.list;
        }

        /** 该集合是不是专用收纳格（或它的剥离子组）。 */
        public boolean isDedicated(RecipeCollection collection) {
            return this.dedicated.contains(collection);
        }

        /** 该条目是不是"混合组里已被收纳格收录"的副本（搜索时这类副本在混合组里不再显示）。 */
        public boolean isCopy(RecipeCollection collection, RecipeDisplayId id) {
            if (id == null) {
                return false;
            }
            java.util.Set<RecipeDisplayId> ids = this.copied.get(collection);
            return ids != null && ids.contains(id);
        }

        /** 子组 / 重打包组继承父集合的两份状态（专用格血统 + 副本索引）。 */
        void inherit(RecipeCollection from, RecipeCollection to) {
            if (this.dedicated.contains(from)) {
                this.dedicated.add(to);
            }
            java.util.Set<RecipeDisplayId> ids = this.copied.get(from);
            if (ids != null) {
                this.copied.put(to, ids);
            }
        }
    }

    /**
     * ⏸️ **2026-09-30 起暂停继续开发**（功能本身按用户指令**默认开启**，显示名「自动收纳同产物配方」）。
     * 开发过程与重启路线图见
     * {@code docs/同产物配方合并-开发过程记录.md}；此处保留完整实现与 {@code [BRBE-MERGE]} 诊断日志。
     *
     * **同产物合并 = 专用收纳格**（用户 2026-09-28 定稿："既要新建专用收纳组，又要保持原有的秩序"）。
     *
     * <ol>
     *   <li><b>路线族拼接</b>（第 0 步，见 {@link #coalesceRouteFamilies}）：互为"平行路线"的
     *       混合组（harness 的羊毛路线 + 交叉染色、carpet/bed 的 {@code _dye} 等）先拼成
     *       **一个**集合 —— 产物因此不再跨组，也就不会各得一个收纳格（原版这三个族本是
     *       16 色各一格 = 48 格，拼接后为 0；两个原组 → 一个组）。</li>
     *   <li><b>组内同产物相邻</b>（第 0.5 步，见 {@link #applyResultClustering}）：每个组里同一
     *       产物的多条做法排在一起（产物按首次出现顺序，产物内部保持原相对顺序），不再"一前一后
     *       中间夹着别的产物"。</li>
     *   <li><b>非混合组</b>（独立格 / 全同产物组）里的同产物配方 → **搬进**收纳格；它若是该产物
     *       唯一的非混合格，就**原地当收纳格**（位置不变），多个非混合格则并成一格。</li>
     *   <li><b>混合组</b>里的同产物配方 → **复制**一份进收纳格，**原组一条不动**（轮循集合与组内
     *       顺序完全不变）。同一个 {@link RecipeDisplayEntry} 实例被两处引用：entry 是不可变
     *       record，craftable/selected/残缺标记都按**集合对象**记，两份互不干扰。</li>
     *   <li>产物只出现在一个格子里的 → 什么都不做。</li>
     *   <li>收纳格位置：有非混合来源 → 取代第一个非混合格；全靠复制 → 插在第一个来源（混合组）之前。</li>
     * </ol>
     *
     * <p>复制出来的"混合组一份 + 收纳格一份"是**有意**的（保留原秩序）。当配方状态变化让它脱离
     * 父组时（Stage 2.5 的排序原因剥离），{@link #applySortExtraction} 会把两份**合二为一**
     * （见 {@link #dedupeCopiedExtractions}）；搜索时副本在混合组里不再重复显示。</p>
     *
     * <p><b>与「完全拆散替代配方组」的关系</b>：取消分组优先——{@code noGrouped} 开启时本 stage
     * 不做任何事。</p>
     *
     * @param remarkPacks 新建收纳格的回调（原版书用来重放残缺标记：标记按集合对象身份记录）
     */
    public static MergeResult applyResultMerge(
            List<RecipeCollection> collections,
            ContextMap displayContext,
            java.util.function.Consumer<List<RecipeCollection>> remarkPacks) {
        FusedRecipeVariants.beginRun(); // 融合成员表随本次运行重建（管线命中缓存时沿用上一次）
        diagReset();                    // 合并诊断计数随本次运行重置（见 mergeDiagnostics）

        java.util.Set<RecipeCollection> dedicated =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Map<RecipeCollection, java.util.Set<RecipeDisplayId>> copied = new java.util.IdentityHashMap<>();
        if (collections == null || collections.size() < 2 || displayContext == null
                || BetterRecipeBook.config == null) {
            return new MergeResult(collections, dedicated, copied);
        }
        if (BetterRecipeBook.config.alternativeRecipes.noGrouped()
                || !BetterRecipeBook.config.alternativeRecipes.mergeSameResult) {
            return new MergeResult(collections, dedicated, copied);
        }

        List<RecipeCollection> newPacks = new ArrayList<>();

        // 0) 路线族拼接（用户 2026-09-28 定）：互为一对"平行路线"的混合组先拼成一个集合。
        //    拼接后这些产物不再跨组，第 2/3 步的按产物收纳自然不会为它们各建一格。
        List<RecipeCollection> working = coalesceRouteFamilies(collections, displayContext, newPacks);

        // 0.5) 组内同产物相邻（用户 2026-09-29 定）：同一个组里同一产物的多条做法排在一起，
        //      不再"一前一后中间夹着别的产物"。只重建顺序真的会变的集合，源集合不动。
        working = applyResultClustering(working, displayContext, newPacks);

        int n = working.size();

        // 1) 每个集合的产物结构：单产物键（独立格 / 全同产物组）或 null（混合组）
        ResultKey[] uniform = new ResultKey[n];
        java.util.Map<RecipeDisplayEntry, ResultKey> entryKeys = new java.util.IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            List<RecipeDisplayEntry> recipes = working.get(i).getRecipes();
            if (recipes.isEmpty()) {
                continue;
            }
            ResultKey key = null;
            boolean mixed = false;
            for (RecipeDisplayEntry entry : recipes) {
                ResultKey entryKey = ResultKey.of(entry, displayContext);
                if (entryKey == null) {
                    mixed = true; // 解不出产物：该条不参与，整组按混合处理
                    continue;
                }
                entryKeys.put(entry, entryKey);
                if (key == null) {
                    key = entryKey;
                } else if (!key.equals(entryKey)) {
                    mixed = true;
                }
            }
            uniform[i] = mixed ? null : key;
        }

        // 2) 产物 → 来源集合下标（混合组按组内各自的产物登记）
        java.util.Map<ResultKey, List<Integer>> sources = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            if (uniform[i] != null) {
                sources.computeIfAbsent(uniform[i], k -> new ArrayList<>()).add(i);
                continue;
            }
            java.util.Set<ResultKey> seen = new java.util.HashSet<>();
            for (RecipeDisplayEntry entry : working.get(i).getRecipes()) {
                ResultKey entryKey = entryKeys.get(entry);
                if (entryKey != null && seen.add(entryKey)) {
                    sources.computeIfAbsent(entryKey, k -> new ArrayList<>()).add(i);
                }
            }
        }

        // 3) 逐个产物决定专用收纳格
        //    ⚠️ insertBefore 的值必须是**列表**：同一个混合组（同一个下标）里可以有多个产物
        //    各自建格。曾经用 Map<下标, 条目> 存 → 同一组的下标互相覆盖，16 色马铠只剩最后一色
        //    （2026-09-28 实测"只多了一个黄色挽具收纳格"即此因）。
        java.util.Map<Integer, List<List<RecipeDisplayEntry>>> insertBefore = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, List<RecipeDisplayEntry>> replaceAt = new java.util.LinkedHashMap<>();
        java.util.Set<Integer> absorbed = new java.util.HashSet<>();
        for (java.util.Map.Entry<ResultKey, List<Integer>> group : sources.entrySet()) {
            List<Integer> srcs = group.getValue();
            if (srcs.size() < 2) {
                continue; // 只出现在一个格子里 → 不处理
            }
            List<Integer> nonMixed = new ArrayList<>();
            List<Integer> mixed = new ArrayList<>();
            for (int idx : srcs) {
                if (uniform[idx] != null) {
                    nonMixed.add(idx);
                } else {
                    mixed.add(idx);
                }
            }

            List<RecipeDisplayEntry> entries = new ArrayList<>();
            java.util.Set<RecipeDisplayId> ids = new java.util.HashSet<>();
            // ① 非混合来源：搬（顺序 = 原列表序）
            for (int idx : nonMixed) {
                for (RecipeDisplayEntry entry : working.get(idx).getRecipes()) {
                    if (entry.id() != null && ids.add(entry.id())) {
                        entries.add(entry);
                    }
                }
            }
            // ② 混合来源：复制（原组不动），并记录副本 id 供搜索去重
            for (int idx : mixed) {
                java.util.Set<RecipeDisplayId> copyIds = copied.computeIfAbsent(
                        working.get(idx), k -> new java.util.HashSet<>());
                for (RecipeDisplayEntry entry : working.get(idx).getRecipes()) {
                    if (!group.getKey().equals(entryKeys.get(entry))) {
                        continue; // 该组里别的产物的条目
                    }
                    if (entry.id() != null) {
                        copyIds.add(entry.id());
                    }
                    if (entry.id() != null && ids.add(entry.id())) {
                        entries.add(entry);
                    }
                }
            }
            if (entries.size() < 2) {
                continue; // 兜底：不足两条不建格
            }

            if (!nonMixed.isEmpty()) {
                replaceAt.put(nonMixed.get(0), entries); // 原地取代第一个非混合格
                absorbed.addAll(nonMixed);
            } else {
                // 全靠复制：插在第一个来源（混合组）之前；同一组多个产物 → 依次插入，互不覆盖
                insertBefore.computeIfAbsent(mixed.get(0), k -> new ArrayList<>()).add(entries);
            }
        }
        if (replaceAt.isEmpty() && insertBefore.isEmpty()) {
            // ⚠️ 不能在这里直接 return：第 5 步（同产物同形融合）必须照跑，否则"本标签页没有任何
            // 产物跨格"时融合永远不生效（2026-09-29 用户实测：一个融合成功的例子都看不到——
            // 族拼接之后 bed/carpet/harness 这类正是走这条路）。
            List<RecipeCollection> fusedOnly = applySameShapeFusion(working, displayContext, newPacks);
            for (RecipeCollection pack : newPacks) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(pack);
            }
            if (remarkPacks != null && !newPacks.isEmpty()) {
                remarkPacks.accept(newPacks);
            }
            return new MergeResult(fusedOnly, dedicated, copied);
        }

        // 4) 组装输出（源集合对象**不被修改**：搬 = 从输出里去掉原格，源列表保持原样，
        //    这样管线反复运行是幂等的）
        List<RecipeCollection> out = new ArrayList<>(working.size() + insertBefore.size());
        for (int i = 0; i < n; i++) {
            List<List<RecipeDisplayEntry>> before = insertBefore.get(i);
            if (before != null) {
                for (List<RecipeDisplayEntry> entries : before) {
                    RecipeCollection pack = buildPack(entries, working.get(i));
                    dedicated.add(pack);
                    diagCells++;
                    newPacks.add(pack);
                    out.add(pack);
                }
            }
            List<RecipeDisplayEntry> replacement = replaceAt.get(i);
            if (replacement != null) {
                RecipeCollection pack = buildPack(replacement, working.get(i));
                dedicated.add(pack);
                diagCells++;
                newPacks.add(pack);
                out.add(pack);
                continue;
            }
            if (absorbed.contains(i)) {
                continue; // 已被收纳格取代
            }
            out.add(working.get(i));
        }

        // 5) 同产物同形融合（用户 2026-09-29 定，**排在收纳组之后**）：同一产物的多条做法形状一致时
        //    合为一个条目，差异部分用 SlotDisplay.Composite 轮询展示；判据见 applySameShapeFusion。
        out = applySameShapeFusion(out, displayContext, newPacks);

        for (RecipeCollection pack : newPacks) {
            // 新对象没有按身份记录的标记：不兼容标记就地补做（与 ungroup 拆分的处理同源），
            // 残缺标记交给调用方重放
            IncompatibleCraftingUtil.markIncompatibleRecipes(pack);
        }
        if (remarkPacks != null && !newPacks.isEmpty()) {
            remarkPacks.accept(newPacks);
        }
        return new MergeResult(out, dedicated, copied);
    }

    // ---- 合并诊断（只读；供日志与自检，不参与任何行为）----

    /**
     * 最近一次 {@link #applyResultMerge} 的统计。用途：把"开关到底有没有生效、建了几格、
     * 融了几条、哪些产物因为哪条判据没融"写进 {@code logs/brbe-debug.log}
     * （{@code [BRBE-MERGE]} 行）——以前只能靠猜（2026-09-29 用户实测"看不到任何融合"时，
     * 无法区分"开关没开 / 没建格 / 被判据挡下"三种情况）。
     *
     * <p>单线程渲染路径，static 计数即可（每次合并开头重置）。缓存命中时管线不跑，摘要
     * 保持上一次的值——这正是我们要的（那一轮显示的就是那份摘要对应的输出）。</p>
     */
    private static int diagCells;
    private static int diagFusedEntries;
    private static int diagFusedMembers;
    private static int diagRejectedShape;
    private static int diagRejectedResult;
    private static int diagRejectedCoverage;
    private static int diagRejectedOther;
    private static final List<String> diagCoverageSamples = new ArrayList<>();

    /** 合并诊断摘要（给日志用；无副作用）。 */
    public static String mergeDiagnostics() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("cells=").append(diagCells)
                .append(" fused=").append(diagFusedEntries)
                .append("(from ").append(diagFusedMembers).append(" recipes)")
                .append(" rejected[shape=").append(diagRejectedShape)
                .append(" result=").append(diagRejectedResult)
                .append(" coverage=").append(diagRejectedCoverage)
                .append(" other=").append(diagRejectedOther).append(']');
        if (!diagCoverageSamples.isEmpty()) {
            sb.append(" coverageSamples=").append(diagCoverageSamples);
        }
        return sb.toString();
    }

    private static void diagReset() {
        diagCells = 0;
        diagFusedEntries = 0;
        diagFusedMembers = 0;
        diagRejectedShape = 0;
        diagRejectedResult = 0;
        diagRejectedCoverage = 0;
        diagRejectedOther = 0;
        diagCoverageSamples.clear();
    }

    /** {@link #fuseAttempt} 的否决原因（诊断用；只记"整组没融"时最后一次尝试的原因）。 */
    private static final int REJECT_NONE = 0;
    private static final int REJECT_SHAPE = 1;
    private static final int REJECT_RESULT = 2;
    private static final int REJECT_COVERAGE = 3;
    private static final int REJECT_OTHER = 4;
    private static final int REJECT_NON_CRAFTING = 5;

    private static final class Reject {
        int reason = REJECT_NONE;
    }

    /** 路线族的最少共享产物数（小侧至少这么多产物）。 */
    private static final int ROUTE_FAMILY_MIN_SHARED = 2;
    /** 路线族的产物数比例上限：大侧产物数 ≤ 该值 × 小侧，防大组吞掉小组。 */
    private static final int ROUTE_FAMILY_MAX_RATIO = 2;

    /**
     * **路线族拼接**（用户 2026-09-28 定）：互为一对"平行路线"的多个混合组先拼成**一个**集合，
     * 这些产物就不再跨组 —— {@link #applyResultMerge} 第 2/3 步的按产物收纳自然不会为它们
     * 各建一格。原版 harness（羊毛路线 + 交叉染色）、carpet（+ {@code _dye}）、bed（+ {@code _dye}）
     * 就是这个范式：不拼接时 16 个颜色会各得一个收纳格（三个族共 48 格）。
     *
     * <p><b>判据（结构式，与 group 命名无关）</b>：两侧产物都可解、各 ≥2 个、**小侧的产物全被
     * 大侧包含**（地毯 18 产物 ⊇ 染色路线 16 产物，多出的苔藓地毯留在拼接后的组里），且
     * **大侧产物数 ≤ {@value #ROUTE_FAMILY_MAX_RATIO} × 小侧**（防 30 产物的大组吞掉 2 产物的小组）。
     * 三个以上路线（A / A_dye / A_alt）用并查集连锁成一组。</p>
     *
     * <p>⚠️ <b>产物多重性不作否决条件</b>（2026-09-28 二轮修正）：同一产物在组内有多条配方
     * 只说明"这个族里有几条路线"，与"是不是同一族"无关——模组往 vanilla 组里追加一条路线是常态
     * （Aerial Hell 把 16 条床配方写进 vanilla 的 {@code bed} 组，使该组变成"16 产物 × 每产物 2 条"）。
     * 曾要求"每个产物在各自组内只出现一次" → 床族被误判为非路线族 → 退回按产物建 16 个收纳格。</p>
     *
     * <p>拼接 = **字面拼接**（先出现的组的条目在前），位置取族内**最靠前**的那一个；源集合不被
     * 修改（新建集合），因此管线反复运行是幂等的。新集合没有按身份记录的残缺标记，所以并入
     * {@code newPacks} 交给调用方重放。</p>
     */
    private static List<RecipeCollection> coalesceRouteFamilies(
            List<RecipeCollection> collections,
            ContextMap displayContext,
            List<RecipeCollection> newPacks) {
        int n = collections.size();
        List<java.util.Set<ResultKey>> productSets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            java.util.Map<ResultKey, Integer> counts = new java.util.LinkedHashMap<>();
            boolean usable = true;
            for (RecipeDisplayEntry entry : collections.get(i).getRecipes()) {
                ResultKey key = ResultKey.of(entry, displayContext);
                if (key == null) {
                    usable = false; // 有解不出产物的条目 → 产物集合不完整，不参与判定
                    break;
                }
                Integer previous = counts.get(key);
                counts.put(key, previous == null ? 1 : previous + 1);
            }
            java.util.Set<ResultKey> keys = null;
            if (usable && counts.size() >= ROUTE_FAMILY_MIN_SHARED) {
                // 只取产物集合：同一产物有几条配方**不影响**成族判定（模组往 vanilla 组里追加
                // 路线是常态，如 Aerial Hell 的 16 条床配方）
                keys = new java.util.HashSet<>(counts.keySet());
            }
            productSets.add(keys);
        }

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        boolean any = false;
        for (int i = 0; i < n; i++) {
            if (productSets.get(i) == null) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                if (productSets.get(j) != null
                        && isParallelRoute(productSets.get(i), productSets.get(j))) {
                    union(parent, i, j);
                    any = true;
                }
            }
        }
        if (!any) {
            return collections;
        }

        java.util.Map<Integer, List<Integer>> members = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            members.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        List<RecipeCollection> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<Integer> family = members.get(find(parent, i));
            if (family.get(0) != i) {
                continue; // 已并入族内最靠前的那一格
            }
            if (family.size() < 2) {
                out.add(collections.get(i));
                continue;
            }
            List<RecipeDisplayEntry> entries = new ArrayList<>();
            for (int member : family) {
                entries.addAll(collections.get(member).getRecipes());
            }
            // 字面拼接后同一产物的多条路线会相隔很远（A 组全部 + B 组全部）→ 就地按产物聚拢
            RecipeCollection pack = buildPack(clusteredEntries(entries, displayContext), collections.get(i));
            newPacks.add(pack);
            out.add(pack);
        }
        return out;
    }

    /**
     * **组内同产物相邻**（用户 2026-09-29 定）：每个组里同一产物的多条做法排在一起，不再被别的
     * 产物夹在中间。产物按**首次出现的顺序**排列，同一产物内部保持原相对顺序（原组的做法在前、
     * 追加的做法在后），因此轮循顺序是可预期的：黑床（羊毛线 → 木板线 → 交叉染色）→ 蓝床 → …
     *
     * <p>只重建**顺序真的会变**的集合（每产物只有一条的组一律原样保留）；源集合不被修改 —— 与
     * 合并本身同一原则，所以关掉开关立刻恢复原顺序。重建出的组是普通组（不是收纳格），按
     * {@code newPacks} 统一补做不兼容标记、交调用方重放残缺标记。</p>
     */
    private static List<RecipeCollection> applyResultClustering(
            List<RecipeCollection> collections,
            ContextMap displayContext,
            List<RecipeCollection> newPacks) {
        List<RecipeCollection> out = null;
        for (int i = 0; i < collections.size(); i++) {
            RecipeCollection collection = collections.get(i);
            List<RecipeDisplayEntry> recipes = collection.getRecipes();
            List<RecipeDisplayEntry> clustered = clusteredEntries(recipes, displayContext);
            if (clustered == recipes) {
                if (out != null) {
                    out.add(collection); // 顺序不变 → 沿用原对象（保住按身份记录的标记）
                }
                continue;
            }
            if (out == null) {
                out = new ArrayList<>(collections.subList(0, i));
            }
            RecipeCollection pack = buildPack(clustered, collection);
            newPacks.add(pack);
            out.add(pack);
        }
        return out == null ? collections : out;
    }

    /**
     * 把一个条目列表按产物聚拢：产物按首次出现顺序排列，同一产物内部保持原相对顺序；
     * 解不出产物的条目留在原来的位置。**每个产物只有一条时原样返回入参**（不重建、不复制）。
     */
    private static List<RecipeDisplayEntry> clusteredEntries(
            List<RecipeDisplayEntry> recipes, ContextMap displayContext) {
        int size = recipes.size();
        if (size < 2) {
            return recipes;
        }
        java.util.Map<ResultKey, List<RecipeDisplayEntry>> groups = new java.util.LinkedHashMap<>();
        for (RecipeDisplayEntry entry : recipes) {
            ResultKey key = ResultKey.of(entry, displayContext);
            if (key != null) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
        }
        if (groups.size() == size) {
            return recipes; // 每产物一条 → 已经是聚拢的
        }
        List<RecipeDisplayEntry> out = new ArrayList<>(size);
        java.util.Set<ResultKey> emitted = new java.util.HashSet<>();
        for (RecipeDisplayEntry entry : recipes) {
            ResultKey key = ResultKey.of(entry, displayContext);
            if (key == null) {
                out.add(entry); // 产物解不出：不参与聚拢，留在原位
                continue;
            }
            if (emitted.add(key)) {
                out.addAll(groups.get(key));
            }
        }
        for (int i = 0; i < size; i++) {
            if (out.get(i) != recipes.get(i)) {
                return out; // 顺序确实变了
            }
        }
        return recipes;
    }

    // ---- Stage 2.6 第 5 步：同产物同形融合 ----

    /**
     * **同产物同形融合**（用户 2026-09-29 定，排在收纳组之后）：同一产物的多条做法，若"合成形状"一致
     * 就**合为一个条目**，差异部分用 {@code SlotDisplay.Composite}（原版"多物品原料"的轮循表示）
     * 轮流展示 —— 不需要任何新的渲染代码。
     *
     * <p><b>形状判据</b>：shaped↔shaped = **去掉空行空列**后的图案（含宽高）一致；shapeless↔shapeless =
     * 材料条数一致；shaped↔shapeless = 材料数一致（shaped 按非空格数），成品取 **shaped 那版的形状**，
     * shapeless 的材料按"选项重叠最大"配对落到形状的格子上。产物 result display 也要一致
     * （同产物但数量/组件不同则不融合）。</p>
     *
     * <p><b>附加硬条件</b>（用户 2026-09-29 定）：融合后"逐槽选项的笛卡尔积"里**每一个组合都必须是
     * 成员之一真实允许的组合**，否则直接放弃融合。反例：蛋糕一号（中心 3 种鸡蛋 × 顶排原版奶桶）与
     * 蛋糕二号（中心 鸡蛋 × 顶排模组奶桶）——"模组奶桶 + 红/蓝鸡蛋"在积里却没有对应配方 → 不融合。
     * 等价判据：设并集 U_j、成员选项集 S_i,j、D_i = {j : S_i,j ⊊ U_j}，则积被覆盖 ⟺
     * **某成员 D_i = ∅**（它自己就允许整个积）或 **所有成员都只在同一个槽上不完整**
     * （|D_i| = 1 且槽位相同）。</p>
     *
     * <p>融合条目的 id = **主成员**（成员里有被 pin 的优先）的真实 id；requirements 取逐槽物品并集，
     * 于是"可合成 / 残缺 / 不可合成"三态天然等于成员里的最高优先级（并集的每一槽都是成员选项的并）。
     * pin 状态经 {@link FusedRecipeVariants} 跟随成员（融合条目按成员 pin 键判定）。</p>
     */
    private static List<RecipeCollection> applySameShapeFusion(
            List<RecipeCollection> collections,
            ContextMap displayContext,
            List<RecipeCollection> newPacks) {
        List<RecipeCollection> out = null;
        for (int i = 0; i < collections.size(); i++) {
            RecipeCollection collection = collections.get(i);
            List<RecipeDisplayEntry> entries = collection.getRecipes();
            List<RecipeDisplayEntry> fused = fuseSameShapeEntries(entries, displayContext);
            if (fused == entries) {
                if (out != null) {
                    out.add(collection);
                }
                continue;
            }
            if (out == null) {
                out = new ArrayList<>(collections.subList(0, i));
            }
            RecipeCollection pack = buildPack(fused, collection);
            newPacks.add(pack);
            out.add(pack);
        }
        return out == null ? collections : out;
    }

    /** 逐集合融合"同一产物 + 形状一致"的条目；没有任何融合时**原样返回入参**（不重建）。 */
    private static List<RecipeDisplayEntry> fuseSameShapeEntries(
            List<RecipeDisplayEntry> entries, ContextMap displayContext) {
        if (entries.size() < 2) {
            return entries;
        }
        java.util.Map<ResultKey, List<RecipeDisplayEntry>> byResult = new java.util.LinkedHashMap<>();
        for (RecipeDisplayEntry entry : entries) {
            ResultKey key = ResultKey.of(entry, displayContext);
            if (key != null) {
                byResult.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
        }
        boolean hasPair = false;
        for (List<RecipeDisplayEntry> group : byResult.values()) {
            if (group.size() >= 2) {
                hasPair = true;
                break;
            }
        }
        if (!hasPair) {
            return entries;
        }

        List<RecipeDisplayEntry> out = new ArrayList<>(entries.size());
        java.util.Set<ResultKey> emitted = new java.util.HashSet<>();
        boolean fusedAny = false;
        for (RecipeDisplayEntry entry : entries) {
            ResultKey key = ResultKey.of(entry, displayContext);
            if (key == null) {
                out.add(entry); // 产物解不出 → 不参与融合
                continue;
            }
            if (!emitted.add(key)) {
                continue; // 该产物的组已输出
            }
            List<RecipeDisplayEntry> group = byResult.get(key);
            List<RecipeDisplayEntry> fusedGroup = group == null ? null : fuseGroup(group, displayContext);
            if (fusedGroup == null) {
                out.addAll(group);
                continue;
            }
            fusedAny = true;
            out.addAll(fusedGroup);
        }
        return fusedAny ? out : entries;
    }

    /**
     * 一个产物组内的贪心融合：以首个未处理条目为种子，依次吸收后面"形状兼容且积可覆盖"的条目；
     * 一组 ≥2 条才真正融合。返回该组的新条目列表（无融合 → {@code null}）。
     */
    private static List<RecipeDisplayEntry> fuseGroup(List<RecipeDisplayEntry> group, ContextMap displayContext) {
        int size = group.size();
        List<FusionShape> shapes = new ArrayList<>(size);
        Reject reject = new Reject();
        for (RecipeDisplayEntry entry : group) {
            FusionShape shape = fusionShapeOf(entry);
            if (shape == null) {
                diagRejectedOther++; // 非合成台 display（熔炉/切石/锻造/自定义）→ 整组不融合
                return null;
            }
            shapes.add(shape);
        }
        boolean[] used = new boolean[size];
        List<RecipeDisplayEntry> out = new ArrayList<>(size);
        boolean fusedAny = false;
        for (int i = 0; i < size; i++) {
            if (used[i]) {
                continue;
            }
            List<Integer> picked = new ArrayList<>();
            picked.add(i);
            for (int j = i + 1; j < size; j++) {
                if (used[j]) {
                    continue;
                }
                picked.add(j);
                if (fuseAttempt(shapes, picked, displayContext, reject) == null) {
                    picked.remove(picked.size() - 1);
                }
            }
            if (picked.size() >= 2) {
                RecipeDisplayEntry fused = fuseAttempt(shapes, picked, displayContext, reject);
                if (fused != null) {
                    out.add(fused);
                    for (int index : picked) {
                        used[index] = true;
                    }
                    fusedAny = true;
                    diagFusedEntries++;
                    diagFusedMembers += picked.size();
                    continue;
                }
            }
            out.add(group.get(i));
            used[i] = true;
        }
        if (!fusedAny && size >= 2) {
            // 整组一条都没融 → 记一次否决（原因取最后一次尝试）——诊断用，不影响行为
            switch (reject.reason) {
                case REJECT_SHAPE -> diagRejectedShape++;
                case REJECT_RESULT -> diagRejectedResult++;
                case REJECT_COVERAGE -> {
                    diagRejectedCoverage++;
                    if (diagCoverageSamples.size() < 12) {
                        ResultKey key = ResultKey.of(group.get(0), displayContext);
                        diagCoverageSamples.add(key == null ? "?" : key.describe());
                    }
                }
                case REJECT_NON_CRAFTING -> diagRejectedOther++;
                default -> diagRejectedOther++;
            }
        }
        return fusedAny ? out : null;
    }

    /** 尝试把 picked 成员融合成一个条目：形状兼容 + 逐槽选项积可覆盖 → 融合条目；否则 {@code null}。 */
    private static RecipeDisplayEntry fuseAttempt(
            List<FusionShape> shapes, List<Integer> picked, ContextMap displayContext, Reject reject) {
        int referenceIndex = -1;
        for (int index : picked) {
            if (shapes.get(index).shaped) {
                referenceIndex = index; // 成品取 shaped 那版的形状
                break;
            }
        }
        if (referenceIndex < 0) {
            referenceIndex = picked.get(0); // 全 shapeless：以第一条为参考
        }
        FusionShape reference = shapes.get(referenceIndex);
        for (int index : picked) {
            FusionShape shape = shapes.get(index);
            if (reference.shaped) {
                if (shape.shaped) {
                    if (shape.width != reference.width || shape.height != reference.height
                            || !java.util.Arrays.equals(shape.filled, reference.filled)) {
                        reject.reason = REJECT_SHAPE;
                        return null; // 形状不一致
                    }
                } else if (shape.materialCount() != reference.materialCount()) {
                    reject.reason = REJECT_SHAPE;
                    return null; // shaped + shapeless：材料数必须相同
                }
            } else if (shape.materialCount() != reference.materialCount()) {
                reject.reason = REJECT_SHAPE;
                return null; // 全 shapeless：材料条数必须相同
            }
            if (!shape.result.equals(reference.result)) {
                reject.reason = REJECT_RESULT;
                return null; // 同产物但 result display 不同（数量/组件）→ 不融合
            }
        }
        List<AlignedMember> aligned = new ArrayList<>(picked.size());
        for (int index : picked) {
            AlignedMember member = alignMember(reference, shapes.get(index), displayContext);
            if (member == null) {
                reject.reason = REJECT_OTHER;
                return null;
            }
            aligned.add(member);
        }
        List<java.util.Set<ItemStack>> union = unionOf(aligned, reference.slotCount());
        if (!productCovered(aligned, union)) {
            reject.reason = REJECT_COVERAGE;
            return null; // 积里存在没有对应配方的组合 → 放弃融合
        }
        return buildFusedEntry(reference, aligned, union);
    }

    /**
     * 融合后"逐槽选项的笛卡尔积"是否被成员们完全覆盖（等价判据见 {@link #applySameShapeFusion}）。
     */
    private static boolean productCovered(
            List<AlignedMember> members, List<java.util.Set<ItemStack>> union) {
        for (AlignedMember member : members) {
            boolean complete = true;
            for (int slot = 0; slot < union.size(); slot++) {
                if (!union.get(slot).equals(member.options.get(slot))) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                return true; // 该成员自己就允许整个积
            }
        }
        int common = -1;
        for (AlignedMember member : members) {
            int incompleteCount = 0;
            int incompleteSlot = -1;
            for (int slot = 0; slot < union.size(); slot++) {
                if (!union.get(slot).equals(member.options.get(slot))) {
                    incompleteCount++;
                    incompleteSlot = slot;
                }
            }
            if (incompleteCount != 1) {
                return false; // 两个以上槽不完整 → 必然能拼出没有配方的组合
            }
            if (common < 0) {
                common = incompleteSlot;
            } else if (common != incompleteSlot) {
                return false; // 不完整槽不一致 → 同上
            }
        }
        return true;
    }

    /** 把成员对齐到参考形状：同形 shaped 逐格对齐；shapeless 按"选项重叠最大"配对落到参考的非空格。 */
    private static AlignedMember alignMember(FusionShape reference, FusionShape member, ContextMap displayContext) {
        int slots = reference.slotCount();
        List<java.util.Set<ItemStack>> options = new ArrayList<>(slots);
        List<net.minecraft.world.item.crafting.display.SlotDisplay> displays = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            options.add(new java.util.LinkedHashSet<>());
            displays.add(net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE);
        }

        if (reference.shaped && member.shaped) {
            if (reference.width != member.width || reference.height != member.height) {
                return null;
            }
            for (int i = 0; i < slots; i++) {
                if (reference.filled[i] != member.filled[i]) {
                    return null; // 非空格分布不一致
                }
                if (reference.filled[i]) {
                    displays.set(i, member.cells.get(i));
                    options.get(i).addAll(itemsOf(member.cells.get(i), displayContext));
                }
            }
            return new AlignedMember(member.entry, options, displays);
        }

        List<Integer> referenceSlots = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            if (!reference.shaped || reference.filled[i]) {
                referenceSlots.add(i);
            }
        }
        if (member.cells.size() != referenceSlots.size()) {
            return null; // 材料数不一致
        }
        List<java.util.Set<ItemStack>> memberOptions = new ArrayList<>(member.cells.size());
        for (net.minecraft.world.item.crafting.display.SlotDisplay cell : member.cells) {
            memberOptions.add(itemsOf(cell, displayContext));
        }
        List<java.util.Set<ItemStack>> targetOptions = new ArrayList<>(referenceSlots.size());
        for (int slot : referenceSlots) {
            targetOptions.add(itemsOf(reference.cells.get(slot), displayContext));
        }
        int[] pairing = bestPairing(memberOptions, targetOptions);
        if (pairing == null) {
            return null;
        }
        for (int i = 0; i < member.cells.size(); i++) {
            int slot = referenceSlots.get(pairing[i]);
            displays.set(slot, member.cells.get(i));
            options.get(slot).addAll(memberOptions.get(i));
        }
        return new AlignedMember(member.entry, options, displays);
    }

    /**
     * 贪心 + 2-opt 的最佳配对（source → target 的下标映射），按 Jaccard 重叠最大化；
     * 配对只影响判据的严格程度（配得差 → 更保守），不会造出错误的融合。
     */
    private static int[] bestPairing(
            List<java.util.Set<ItemStack>> source, List<java.util.Set<ItemStack>> target) {
        int size = source.size();
        if (target.size() < size) {
            return null;
        }
        int[] result = new int[size];
        boolean[] usedSource = new boolean[size];
        boolean[] usedTarget = new boolean[target.size()];
        for (int step = 0; step < size; step++) {
            int bestSource = -1;
            int bestTarget = -1;
            double bestScore = -1.0D;
            for (int i = 0; i < size; i++) {
                if (usedSource[i]) {
                    continue;
                }
                for (int j = 0; j < target.size(); j++) {
                    if (usedTarget[j]) {
                        continue;
                    }
                    double score = overlap(source.get(i), target.get(j));
                    if (score > bestScore) {
                        bestScore = score;
                        bestSource = i;
                        bestTarget = j;
                    }
                }
            }
            if (bestSource < 0) {
                return null;
            }
            result[bestSource] = bestTarget;
            usedSource[bestSource] = true;
            usedTarget[bestTarget] = true;
        }
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                double current = overlap(source.get(i), target.get(result[i]))
                        + overlap(source.get(j), target.get(result[j]));
                double swapped = overlap(source.get(i), target.get(result[j]))
                        + overlap(source.get(j), target.get(result[i]));
                if (swapped > current + 1.0E-9D) {
                    int swap = result[i];
                    result[i] = result[j];
                    result[j] = swap;
                }
            }
        }
        return result;
    }

    private static double overlap(java.util.Set<ItemStack> a, java.util.Set<ItemStack> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0D;
        }
        int intersection = 0;
        for (ItemStack stack : a) {
            if (b.contains(stack)) {
                intersection++;
            }
        }
        int unionSize = a.size() + b.size() - intersection;
        return unionSize == 0 ? 0.0D : (double) intersection / (double) unionSize;
    }

    private static List<java.util.Set<ItemStack>> unionOf(List<AlignedMember> members, int slots) {
        List<java.util.Set<ItemStack>> union = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            java.util.Set<ItemStack> merged = new java.util.LinkedHashSet<>();
            for (AlignedMember member : members) {
                merged.addAll(member.options.get(slot));
            }
            union.add(merged);
        }
        return union;
    }

    /** 融合条目：id/group/category 取主成员（成员里有被 pin 的优先 → pin 状态跟随）。 */
    private static RecipeDisplayEntry buildFusedEntry(
            FusionShape reference, List<AlignedMember> aligned, List<java.util.Set<ItemStack>> union) {
        int slots = reference.slotCount();
        List<net.minecraft.world.item.crafting.display.SlotDisplay> slotDisplays = new ArrayList<>(slots);
        List<net.minecraft.world.item.crafting.Ingredient> requirements = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            List<net.minecraft.world.item.crafting.display.SlotDisplay> distinct = new ArrayList<>(aligned.size());
            for (AlignedMember member : aligned) {
                net.minecraft.world.item.crafting.display.SlotDisplay display = member.displays.get(slot);
                if (!distinct.contains(display)) {
                    distinct.add(display);
                }
            }
            slotDisplays.add(distinct.size() == 1
                    ? distinct.get(0)
                    : new net.minecraft.world.item.crafting.display.SlotDisplay.Composite(distinct));
            if (!reference.shaped || reference.filled[slot]) {
                java.util.Set<net.minecraft.world.level.ItemLike> items = new java.util.LinkedHashSet<>();
                for (ItemStack stack : union.get(slot)) {
                    if (!stack.isEmpty()) {
                        items.add(stack.getItem());
                    }
                }
                if (items.isEmpty()) {
                    return null;
                }
                requirements.add(net.minecraft.world.item.crafting.Ingredient.of(
                        items.toArray(new net.minecraft.world.level.ItemLike[0])));
            }
        }

        RecipeDisplayEntry primary = aligned.get(0).entry;
        if (BetterRecipeBook.pinnedRecipeManager != null) {
            for (AlignedMember member : aligned) {
                if (BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(member.entry)) {
                    primary = member.entry; // 被 pin 的成员当主成员
                    break;
                }
            }
        }
        net.minecraft.world.item.crafting.display.RecipeDisplay display = reference.shaped
                ? new net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay(
                        reference.width, reference.height, slotDisplays, reference.result, reference.station)
                : new net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay(
                        slotDisplays, reference.result, reference.station);
        List<RecipeDisplayEntry> members = new ArrayList<>(aligned.size());
        for (AlignedMember member : aligned) {
            members.add(member.entry);
        }
        RecipeDisplayEntry fused = new RecipeDisplayEntry(primary.id(), display, primary.group(),
                primary.category(), java.util.Optional.of(requirements));
        FusedRecipeVariants.register(fused, members);
        return fused;
    }

    /** 条目的"形状描述"：shaped 取裁剪空行空列后的宽高与逐格 display；shapeless 退化成材料序列。 */
    private static FusionShape fusionShapeOf(RecipeDisplayEntry entry) {
        net.minecraft.world.item.crafting.display.RecipeDisplay display = entry.display();
        if (display instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped) {
            int width = shaped.width();
            int height = shaped.height();
            List<net.minecraft.world.item.crafting.display.SlotDisplay> ingredients = shaped.ingredients();
            if (width <= 0 || height <= 0 || ingredients.size() != width * height) {
                return null; // 数据异常 → 不参与融合
            }
            boolean[] nonEmpty = new boolean[ingredients.size()];
            int minRow = height;
            int maxRow = -1;
            int minColumn = width;
            int maxColumn = -1;
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    boolean has = !isEmptySlot(ingredients.get(row * width + column));
                    nonEmpty[row * width + column] = has;
                    if (has) {
                        minRow = Math.min(minRow, row);
                        maxRow = Math.max(maxRow, row);
                        minColumn = Math.min(minColumn, column);
                        maxColumn = Math.max(maxColumn, column);
                    }
                }
            }
            if (maxRow < 0) {
                return null; // 全空
            }
            int trimmedWidth = maxColumn - minColumn + 1;
            int trimmedHeight = maxRow - minRow + 1;
            List<net.minecraft.world.item.crafting.display.SlotDisplay> cells = new ArrayList<>(trimmedWidth * trimmedHeight);
            boolean[] filled = new boolean[trimmedWidth * trimmedHeight];
            for (int row = 0; row < trimmedHeight; row++) {
                for (int column = 0; column < trimmedWidth; column++) {
                    int source = (row + minRow) * width + (column + minColumn);
                    cells.add(ingredients.get(source));
                    filled[row * trimmedWidth + column] = nonEmpty[source];
                }
            }
            return new FusionShape(entry, true, trimmedWidth, trimmedHeight, cells, filled,
                    shaped.result(), shaped.craftingStation());
        }
        if (display instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless) {
            List<net.minecraft.world.item.crafting.display.SlotDisplay> ingredients = shapeless.ingredients();
            if (ingredients.isEmpty()) {
                return null;
            }
            boolean[] filled = new boolean[ingredients.size()];
            java.util.Arrays.fill(filled, true);
            return new FusionShape(entry, false, ingredients.size(), 1, ingredients, filled,
                    shapeless.result(), shapeless.craftingStation());
        }
        return null; // 只处理合成台的两种 display
    }

    private static boolean isEmptySlot(net.minecraft.world.item.crafting.display.SlotDisplay display) {
        return display == null
                || display instanceof net.minecraft.world.item.crafting.display.SlotDisplay.Empty;
    }

    /** 该槽的候选物品（组件保留、数量归一；解不出时返回空集）。 */
    private static java.util.Set<ItemStack> itemsOf(
            net.minecraft.world.item.crafting.display.SlotDisplay display, ContextMap displayContext) {
        java.util.Set<ItemStack> items = new java.util.LinkedHashSet<>();
        if (display == null || isEmptySlot(display)) {
            return items;
        }
        List<ItemStack> resolved;
        try {
            resolved = display.resolveForStacks(displayContext);
        } catch (Exception e) {
            return items;
        }
        if (resolved != null) {
            for (ItemStack stack : resolved) {
                if (stack != null && !stack.isEmpty()) {
                    items.add(stack.copyWithCount(1));
                }
            }
        }
        return items;
    }

    /** 融合判据用的形状描述。 */
    private static final class FusionShape {
        final RecipeDisplayEntry entry;
        final boolean shaped;
        final int width;
        final int height;
        final List<net.minecraft.world.item.crafting.display.SlotDisplay> cells;
        final boolean[] filled;
        final net.minecraft.world.item.crafting.display.SlotDisplay result;
        final net.minecraft.world.item.crafting.display.SlotDisplay station;

        FusionShape(RecipeDisplayEntry entry, boolean shaped, int width, int height,
                    List<net.minecraft.world.item.crafting.display.SlotDisplay> cells, boolean[] filled,
                    net.minecraft.world.item.crafting.display.SlotDisplay result,
                    net.minecraft.world.item.crafting.display.SlotDisplay station) {
            this.entry = entry;
            this.shaped = shaped;
            this.width = width;
            this.height = height;
            this.cells = cells;
            this.filled = filled;
            this.result = result;
            this.station = station;
        }

        int slotCount() {
            return this.cells.size();
        }

        int materialCount() {
            int count = 0;
            for (boolean value : this.filled) {
                if (value) {
                    count++;
                }
            }
            return count;
        }
    }

    /** 对齐到参考形状之后的成员：逐槽的选项物品集与该成员在该槽的 display。 */
    private static final class AlignedMember {
        final RecipeDisplayEntry entry;
        final List<java.util.Set<ItemStack>> options;
        final List<net.minecraft.world.item.crafting.display.SlotDisplay> displays;

        AlignedMember(RecipeDisplayEntry entry, List<java.util.Set<ItemStack>> options,
                      List<net.minecraft.world.item.crafting.display.SlotDisplay> displays) {
            this.entry = entry;
            this.options = options;
            this.displays = displays;
        }
    }

    /**
     * 平行路线判据：**小侧产物全被大侧包含**、小侧至少 {@link #ROUTE_FAMILY_MIN_SHARED} 个产物，
     * 且大侧不超过小侧的 {@value #ROUTE_FAMILY_MAX_RATIO} 倍（防大组吞小组）。
     * 产物的多重性（组内同一产物有几条配方）不参与判定。
     */
    private static boolean isParallelRoute(java.util.Set<ResultKey> a, java.util.Set<ResultKey> b) {
        java.util.Set<ResultKey> small = a.size() <= b.size() ? a : b;
        java.util.Set<ResultKey> large = small == a ? b : a;
        return small.size() >= ROUTE_FAMILY_MIN_SHARED
                && large.size() <= ROUTE_FAMILY_MAX_RATIO * small.size()
                && large.containsAll(small);
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    /** 并集：根取**靠前的下标**，族的位置与拼接顺序因此稳定。 */
    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
        }
    }

    /**
     * 副本条目**两边都脱离父组**时合二为一（用户 2026-09-28 定）：同一条配方被剥出父组时，
     * 混合组那份与收纳格那份会各生成一个剥离子组 → 只保留**专用格血统**的那一份。
     *
     * <p>只处理"出现在 ≥2 个**剥离子组**里"的副本：仍留在各自父组里的那份不动（那是有意保留的
     * 复制）。去掉后子组变空 → 整格消失；仍重建的格子由调用方重放残缺标记。</p>
     */
    private static List<RecipeCollection> dedupeCopiedExtractions(
            List<RecipeCollection> list,
            java.util.Set<RecipeCollection> children,
            MergeResult merge,
            java.util.function.Consumer<List<RecipeCollection>> remarkPacks) {
        java.util.Map<RecipeDisplayId, List<RecipeCollection>> occurrences = new java.util.LinkedHashMap<>();
        for (RecipeCollection child : children) {
            for (RecipeDisplayEntry entry : child.getRecipes()) {
                if (entry.id() == null || !merge.isCopy(child, entry.id())) {
                    continue;
                }
                occurrences.computeIfAbsent(entry.id(), k -> new ArrayList<>()).add(child);
            }
        }
        java.util.Map<RecipeCollection, java.util.Set<RecipeDisplayId>> remove = new java.util.IdentityHashMap<>();
        for (java.util.Map.Entry<RecipeDisplayId, List<RecipeCollection>> occurrence : occurrences.entrySet()) {
            List<RecipeCollection> holders = occurrence.getValue();
            if (holders.size() < 2) {
                continue;
            }
            RecipeCollection keep = null;
            for (RecipeCollection holder : holders) {
                if (merge.isDedicated(holder)) {
                    keep = holder;
                    break;
                }
            }
            if (keep == null) {
                keep = holders.get(0);
            }
            for (RecipeCollection holder : holders) {
                if (holder != keep) {
                    remove.computeIfAbsent(holder, k -> new java.util.HashSet<>()).add(occurrence.getKey());
                }
            }
        }
        if (remove.isEmpty()) {
            return list;
        }

        List<RecipeCollection> out = new ArrayList<>(list.size());
        List<RecipeCollection> rebuilt = new ArrayList<>();
        for (RecipeCollection collection : list) {
            java.util.Set<RecipeDisplayId> drop = remove.get(collection);
            if (drop == null) {
                out.add(collection);
                continue;
            }
            List<RecipeDisplayEntry> kept = new ArrayList<>();
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (entry.id() == null || !drop.contains(entry.id())) {
                    kept.add(entry);
                }
            }
            if (kept.isEmpty()) {
                continue; // 整格消失
            }
            RecipeCollection pack = buildPack(kept, collection);
            merge.inherit(collection, pack);
            rebuilt.add(pack);
            out.add(pack);
        }
        if (remarkPacks != null && !rebuilt.isEmpty()) {
            remarkPacks.accept(rebuilt);
        }
        return out;
    }

    /**
     * 产物键：一组产物的 **物品 + 组件**（**忽略数量**——用户 2026-09-28 定）。
     *
     * <p>数量归一化成 1 再比较，因此"1 个"与"4 个"算同一产物；组件走
     * {@link ItemStack#isSameItemSameComponents}（同物品不同附魔/药水不合并）。
     * 哈希用 {@code Item + 组件表}（{@code PatchedDataComponentMap} 的 equals/hashCode
     * 是值语义，javap 核对过）。</p>
     */
    private static final class ResultKey {
        private final List<ItemStack> stacks;

        private ResultKey(List<ItemStack> stacks) {
            this.stacks = stacks;
        }

        /** 条目产物键；产物为空 / 解算失败返回 {@code null}。 */
        static ResultKey of(RecipeDisplayEntry entry, ContextMap displayContext) {
            List<ItemStack> results;
            try {
                results = entry.resultItems(displayContext);
            } catch (Exception e) {
                return null;
            }
            if (results == null || results.isEmpty()) {
                return null;
            }
            List<ItemStack> normalized = new ArrayList<>(results.size());
            for (ItemStack stack : results) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                normalized.add(stack.copyWithCount(1)); // 忽略数量
            }
            return normalized.isEmpty() ? null : new ResultKey(normalized);
        }

        /** 诊断用：产物的物品注册名（多个产物用 {@code +} 连接）。 */
        String describe() {
            StringBuilder sb = new StringBuilder();
            for (ItemStack stack : this.stacks) {
                if (sb.length() > 0) {
                    sb.append('+');
                }
                sb.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ResultKey key)) return false;
            if (this.stacks.size() != key.stacks.size()) return false;
            for (int i = 0; i < this.stacks.size(); i++) {
                if (!ItemStack.isSameItemSameComponents(this.stacks.get(i), key.stacks.get(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            for (ItemStack stack : this.stacks) {
                hash = 31 * hash + stack.getItem().hashCode();
                hash = 31 * hash + stack.getComponents().hashCode();
            }
            return hash;
        }
    }

    // ---- Stage 2.5: 排序原因剥离（替代配方组按变体类别拆分）----

    /**
     * **剥离 stage**（用户 2026-09-27 诉求；取代原 Stage 6 的 pin 专用剥离）：把替代配方组里
     * "需要调整排序"的变体从原组剥出来、按类别各自成组，原组只保留基线（最低类别）变体且
     * **位置不变**；子组再参与后面的正常排序（{@link #applyPins} 置顶 / {@link #applyPartialSort}
     * 可合成 → 残缺 → 其余），并**递归**再剥离（多层继承）。
     *
     * <p>类别取自 {@link SortCategory}：{@code PINNED > CRAFTABLE > PARTIAL > NORMAL}。
     * 既有的 pin 行为（1 个 pin 独立成组 / ≥2 个成组置顶 / 全 pin 原组即 pin 组）是本机制的
     * 一个特例，规则与结果不变。</p>
     *
     * <p><b>搜索</b>：未命中的变体视为不可见（与 {@link #applySearch} 共用
     * {@link #entryMatches} 判据）——原组因此可能被整体丢弃，界面上只留下命中的那部分
     * （用户选择："原组隐藏，只展示命中的子组"）。</p>
     *
     * <p><b>收纳格同样适用</b>（用户 2026-09-30 指令）：{@link MergeResult#isDedicated} 的专用收纳格
     * **不是特例** —— 格内某个变体的状态改变（pin / 可合成 / 残缺）时，它照常被剥出去参与排序，
     * 格内只留基线（最低类别）的那部分。曾经试过"收纳格整格取最高类别、格内不拆"，那会挡住这条
     * 智能拆分机制（组内状态变了却不拆），已撤回。</p>
     *
     * @param query          当前搜索词（非搜索传 {@code null}）
     * @param displayContext 搜索结果解算上下文（非搜索传 {@code null}）
     * @param remarkPacks    新建的子组/重打包组回调（原版书用来重放残缺标记：标记按
     *                       <b>集合对象身份</b>记录，新对象不重放的话组内残缺配方会退化成
     *                       不可合成——即 2026-08-27 一百一十轮那个坑）
     * @param merge          Stage 2.6 的产物（专用格血统 + 副本索引）；为 {@code null} 时
     *                       不做副本去重（诊断路径）
     */
    public static List<RecipeCollection> applySortExtraction(
            List<RecipeCollection> collections,
            SearchQuery query,
            ContextMap displayContext,
            java.util.function.Consumer<List<RecipeCollection>> remarkPacks,
            MergeResult merge) {
        if (collections == null || collections.isEmpty()) {
            return collections;
        }
        boolean searching = query != null && displayContext != null;
        SearchCache cache = searching ? new SearchCache() : null;
        // 剥离子组集合（身份）：只有"两边都脱离父组"的副本才去重，留在父组里的那份不动
        java.util.Set<RecipeCollection> children =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<RecipeCollection> extracted = RecipeExtraction.extract(
                collections, new RecipeExtraction.Plan<RecipeCollection, RecipeDisplayEntry>() {

            @Override
            public List<RecipeDisplayEntry> entries(RecipeCollection collection) {
                return collection.getRecipes();
            }

            @Override
            public SortCategory category(RecipeCollection collection, RecipeDisplayEntry entry) {
                if (entry == null) return SortCategory.NORMAL;
                if (BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(entry)) {
                    return SortCategory.PINNED;
                }
                // ⚠️ 收纳格**不走特例**（2026-09-30 用户指令）：格内某个变体状态改变时照常剥出。
                // （曾经的"整格取最高类别、格内不拆"会挡住这条智能拆分机制，已撤回。）
                // ⚠️ 残缺必须**先于**可合成判定（2026-09-27 用户实测："残缺配方还是会和
                // 可合成配方并在一起"）。残缺配方会被注入 craftable 集合
                // （incompletecrafting/RecipeBookComponentMixin 的 "Inject partial recipes
                // into craftable set"），因此 collection.isCraftable(id) 对残缺变体同样为
                // true——先判可合成会把残缺变体归到 CRAFTABLE、与真可合成变体并进同一个
                // 子组。Stage 4 的 categorizeEvenIfStale 用的正是同一优先级（partial 优先于
                // truly craftable），两处必须一致，否则排序桶与剥离分组会互相矛盾。
                // EvenIfStale：分代推进后不误判（与 Stage 4 排序同一判据）
                if (PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, entry.id())) {
                    return SortCategory.PARTIAL;
                }
                if (collection.isCraftable(entry.id())) {
                    return SortCategory.CRAFTABLE;
                }
                return SortCategory.NORMAL;
            }

            @Override
            public boolean visible(RecipeCollection collection, RecipeDisplayEntry entry) {
                // 搜索时：已被专用收纳格收录的副本在混合组里不再重复显示（用户 2026-09-28 定）。
                // 若该组因此没有任何可见条目，{@code RecipeExtraction} 会把整组丢弃。
                if (searching && merge != null && entry != null
                        && merge.isCopy(collection, entry.id())) {
                    return false;
                }
                return !searching || entryMatches(entry, query, displayContext, cache);
            }

            @Override
            public RecipeCollection subset(RecipeCollection parent, List<RecipeDisplayEntry> entries) {
                RecipeCollection sub = buildPack(entries, parent);
                if (merge != null) {
                    merge.inherit(parent, sub); // 专用格血统 / 副本索引随 subset 传递
                }
                return sub;
            }

            @Override
            public void onPacksCreated(List<RecipeCollection> packs) {
                if (remarkPacks != null) {
                    remarkPacks.accept(packs);
                }
            }

            @Override
            public void onSubgroupPack(RecipeCollection parent, RecipeCollection pack) {
                children.add(pack);
            }
        });
        if (merge == null || children.isEmpty()) {
            return extracted;
        }
        // 副本"两边都脱离父组"→ 合二为一（保留专用格那一份）
        return dedupeCopiedExtractions(extracted, children, merge, remarkPacks);
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
