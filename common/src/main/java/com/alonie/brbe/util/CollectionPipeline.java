package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.generic.pins.PipelineCollection;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight, deterministic pipeline for transforming the recipe collection
 * list before it is passed to {@code RecipeBookPage.updateCollections()}.
 *
 * <p>Each stage is a pure function (or mutates in-place where documented).
 * This replaces the monolithic {@code UpdateCollectionsPipeline} with a
 * simpler design matching the 1.21.11 architectural pattern.
 */
public final class CollectionPipeline {

    private CollectionPipeline() {}

    // ═══════════════════════════════════════════════════════════════
    // Stage 1: Advanced search filter
    // ═══════════════════════════════════════════════════════════════

    /**
     * Filters collections to those whose recipe results match the advanced
     * search query.  Returns the original list unchanged when no query is
     * active or the level is unavailable.
     */
    public static List<RecipeCollection> applySearch(
            List<RecipeCollection> collections,
            SearchQuery query,
            Level level) {
        if (query == null || level == null) return collections;

        var registryAccess = level.registryAccess();
        SearchCache cache = new SearchCache();
        List<RecipeCollection> filtered = new ArrayList<>();

        for (RecipeCollection coll : collections) {
            for (RecipeHolder<?> holder : coll.getRecipes()) {
                ItemStack result = holder.value().getResultItem(registryAccess);
                if (result != null && !result.isEmpty()
                        && query.matches(result, cache)) {
                    filtered.add(coll);
                    break;
                }
            }
        }
        return filtered;
    }

    // ═══════════════════════════════════════════════════════════════
    // Stage 2: Ungroup split
    // ═══════════════════════════════════════════════════════════════

    /**
     * Splits multi-recipe collections into single-recipe collections when
     * {@code alternativeRecipes.noGrouped()} is enabled.  Returns a new list.
     */
    public static List<RecipeCollection> applyUngroup(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.ctx().config().alternativeRecipes.noGrouped()) {
            return collections;
        }

        List<RecipeCollection> split = new ArrayList<>(collections.size());
        for (RecipeCollection collection : collections) {
            List<RecipeHolder<?>> recipes = collection.getRecipes();
            if (recipes.size() <= 1) {
                split.add(collection);
                continue;
            }

            var source = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) collection;
            boolean restrictToCraftableOrPartial = PartialCraftingUtil.hasPartialMaterials(collection)
                    || collection.hasCraftable();
            boolean addedAny = false;

            for (RecipeHolder<?> recipe : recipes) {
                // Only split recipes that fit dimensions
                boolean fits = false;
                for (var h : source.getFitsDimensions()) {
                    if (h.id().equals(recipe.id())) { fits = true; break; }
                }
                if (!fits) continue;

                boolean isCraftable = false;
                for (var h : source.brbe$getCraftable()) {
                    if (h.id().equals(recipe.id())) { isCraftable = true; break; }
                }
                boolean isPartial = PartialCraftingUtil.isPartiallyCraftable(collection, recipe.id());
                if (restrictToCraftableOrPartial && !isCraftable && !isPartial) {
                    continue;
                }

                RecipeCollection child = new RecipeCollection(
                        collection.registryAccess(),
                        Collections.singletonList(recipe));
                if (isCraftable) {
                    var ca = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) child;
                    ca.brbe$getCraftable().add(recipe);
                }
                if (isPartial) {
                    PartialCraftingUtil.markPartialMaterial(child, recipe.id());
                }
                // Populate fitsDimensions so the child collection displays properly.
                // Without this, the child appears as an empty group because vanilla
                // checks fitsDimensions to determine which recipes are visible.
                {
                    var childAcc = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) child;
                    childAcc.getFitsDimensions().add(recipe);
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

    // ═══════════════════════════════════════════════════════════════
    // Stage 2.6: 同产物合并 = 专用收纳格（用户 2026-09-28 定稿）
    // ═══════════════════════════════════════════════════════════════

    /**
     * Stage 2.6 的产物：新列表 + 两份额外状态（按**集合身份**）。
     *
     * <p>{@code dedicated}：专用收纳格（含它们的剥离子组血统）；{@code copied}：混合组 →
     * 该组里"已复制进收纳格"的条目 id。生命周期 = 单次管线调用（调用点持有）。</p>
     */
    public static final class MergeResult {
        private final List<RecipeCollection> list;
        private final java.util.Set<RecipeCollection> dedicated;
        private final java.util.Map<RecipeCollection, java.util.Set<ResourceLocation>> copied;

        private MergeResult(List<RecipeCollection> list,
                            java.util.Set<RecipeCollection> dedicated,
                            java.util.Map<RecipeCollection, java.util.Set<ResourceLocation>> copied) {
            this.list = list;
            this.dedicated = dedicated;
            this.copied = copied;
        }

        public List<RecipeCollection> list() {
            return this.list;
        }

        /** 该集合是不是专用收纳格（或它的剥离子组）。 */
        public boolean isDedicated(RecipeCollection collection) {
            return this.dedicated.contains(collection);
        }

        /** 该配方是不是"混合组里已被收纳格收录"的副本。 */
        public boolean isCopy(RecipeCollection collection, ResourceLocation id) {
            if (id == null) {
                return false;
            }
            java.util.Set<ResourceLocation> ids = this.copied.get(collection);
            return ids != null && ids.contains(id);
        }

        /** 子组 / 重打包组继承父集合的两份状态（专用格血统 + 副本索引）。 */
        void inherit(RecipeCollection from, RecipeCollection to) {
            if (this.dedicated.contains(from)) {
                this.dedicated.add(to);
            }
            java.util.Set<ResourceLocation> ids = this.copied.get(from);
            if (ids != null) {
                this.copied.put(to, ids);
            }
        }
    }

    /**
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
     *       顺序完全不变）。同一个 {@code RecipeHolder} 被两处引用，集合状态按集合对象记。</li>
     *   <li>产物只出现在一个格子里的 → 什么都不做。</li>
     *   <li>收纳格位置：有非混合来源 → 取代第一个非混合格；全靠复制 → 插在第一个来源（混合组）之前。</li>
     * </ol>
     *
     * <p>复制出来的"混合组一份 + 收纳格一份"是**有意**的；pin 剥离时两份同时脱离父组 →
     * {@link #applyPinCopyGroups(List, MergeResult)} 会合二为一（保留专用格那一份）。</p>
     *
     * <p><b>与「拆散替代配方组」的关系</b>：取消分组优先——{@code noGrouped} 开启时不做任何事。</p>
     */
    public static MergeResult applyResultMerge(List<RecipeCollection> collections) {
        java.util.Set<RecipeCollection> dedicated =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Map<RecipeCollection, java.util.Set<ResourceLocation>> copied = new java.util.IdentityHashMap<>();
        if (collections == null || collections.size() < 2
                || BetterRecipeBook.ctx() == null || BetterRecipeBook.ctx().config() == null) {
            return new MergeResult(collections, dedicated, copied);
        }
        if (BetterRecipeBook.ctx().config().alternativeRecipes.noGrouped()
                || !BetterRecipeBook.ctx().config().alternativeRecipes.mergeSameResult) {
            return new MergeResult(collections, dedicated, copied);
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return new MergeResult(collections, dedicated, copied);
        }
        RegistryAccess registryAccess = level.registryAccess();

        List<RecipeCollection> newPacks = new ArrayList<>();

        // 0) 路线族拼接（用户 2026-09-28 定）：互为一对"平行路线"的混合组先拼成一个集合。
        //    拼接后这些产物不再跨组，第 2/3 步的按产物收纳自然不会为它们各建一格。
        List<RecipeCollection> working = coalesceRouteFamilies(collections, registryAccess, newPacks);

        // 0.5) 组内同产物相邻（用户 2026-09-29 定）：同一个组里同一产物的多条做法排在一起，
        //      不再"一前一后中间夹着别的产物"。只重建顺序真的会变的集合，源集合不动。
        working = applyResultClustering(working, registryAccess, newPacks);

        int n = working.size();

        // 1) 每个集合的产物结构：单产物键（独立格 / 全同产物组）或 null（混合组）
        ResultKey[] uniform = new ResultKey[n];
        java.util.Map<RecipeHolder<?>, ResultKey> entryKeys = new java.util.IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            List<RecipeHolder<?>> recipes = working.get(i).getRecipes();
            if (recipes.isEmpty()) {
                continue;
            }
            ResultKey key = null;
            boolean mixed = false;
            for (RecipeHolder<?> holder : recipes) {
                ResultKey entryKey = ResultKey.of(holder, registryAccess);
                if (entryKey == null) {
                    mixed = true; // 解不出产物：该条不参与，整组按混合处理
                    continue;
                }
                entryKeys.put(holder, entryKey);
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
            for (RecipeHolder<?> holder : working.get(i).getRecipes()) {
                ResultKey entryKey = entryKeys.get(holder);
                if (entryKey != null && seen.add(entryKey)) {
                    sources.computeIfAbsent(entryKey, k -> new ArrayList<>()).add(i);
                }
            }
        }

        // 3) 逐个产物决定专用收纳格
        //    ⚠️ insertBefore 的值必须是**列表**：同一个混合组（同一个下标）里可以有多个产物
        //    各自建格。曾经用 Map<下标, 条目> 存 → 同一组的下标互相覆盖，16 色马铠只剩最后一色
        //    （2026-09-28 实测"只多了一个黄色挽具收纳格"即此因）。
        java.util.Map<Integer, List<RecipeHolder<?>>> replaceAt = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, List<List<RecipeHolder<?>>>> insertBefore = new java.util.LinkedHashMap<>();
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

            List<RecipeHolder<?>> entries = new ArrayList<>();
            java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
            for (int idx : nonMixed) {
                for (RecipeHolder<?> holder : working.get(idx).getRecipes()) {
                    if (holder.id() != null && ids.add(holder.id())) {
                        entries.add(holder);
                    }
                }
            }
            for (int idx : mixed) {
                java.util.Set<ResourceLocation> copyIds = copied.computeIfAbsent(
                        working.get(idx), k -> new java.util.HashSet<>());
                for (RecipeHolder<?> holder : working.get(idx).getRecipes()) {
                    if (!group.getKey().equals(entryKeys.get(holder))) {
                        continue; // 该组里别的产物的条目
                    }
                    if (holder.id() != null) {
                        copyIds.add(holder.id());
                    }
                    if (holder.id() != null && ids.add(holder.id())) {
                        entries.add(holder);
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
            return new MergeResult(working, dedicated, copied);
        }

        // 4) 组装输出（源集合对象**不被修改**：搬 = 从输出里去掉原格，源列表保持原样，
        //    这样管线反复运行是幂等的）
        List<RecipeCollection> out = new ArrayList<>(working.size() + insertBefore.size());
        for (int i = 0; i < n; i++) {
            List<List<RecipeHolder<?>>> before = insertBefore.get(i);
            if (before != null) {
                for (List<RecipeHolder<?>> entries : before) {
                    RecipeCollection pack = buildPack(entries, working.get(i));
                    dedicated.add(pack);
                    newPacks.add(pack);
                    out.add(pack);
                }
            }
            List<RecipeHolder<?>> replacement = replaceAt.get(i);
            if (replacement != null) {
                RecipeCollection pack = buildPack(replacement, working.get(i));
                dedicated.add(pack);
                newPacks.add(pack);
                out.add(pack);
                continue;
            }
            if (absorbed.contains(i)) {
                continue; // 已被收纳格取代
            }
            out.add(working.get(i));
        }
        for (RecipeCollection pack : newPacks) {
            // 新对象（收纳格 / 拼接族）没有按身份记录的标记：不兼容标记就地补做
            // （残缺标记由 Stage 6b 重放）
            IncompatibleCraftingUtil.markIncompatibleRecipes(pack);
        }
        return new MergeResult(out, dedicated, copied);
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
     * 修改（新建集合），因此管线反复运行是幂等的。新集合的残缺标记由 Stage 6b 统一重放。</p>
     */
    private static List<RecipeCollection> coalesceRouteFamilies(
            List<RecipeCollection> collections,
            RegistryAccess registryAccess,
            List<RecipeCollection> newPacks) {
        int n = collections.size();
        List<java.util.Set<ResultKey>> productSets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            java.util.Map<ResultKey, Integer> counts = new java.util.LinkedHashMap<>();
            boolean usable = true;
            for (RecipeHolder<?> holder : collections.get(i).getRecipes()) {
                ResultKey key = ResultKey.of(holder, registryAccess);
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
            List<RecipeHolder<?>> entries = new ArrayList<>();
            for (int member : family) {
                entries.addAll(collections.get(member).getRecipes());
            }
            // 字面拼接后同一产物的多条路线会相隔很远（A 组全部 + B 组全部）→ 就地按产物聚拢
            RecipeCollection pack = buildPack(clusteredEntries(entries, registryAccess), collections.get(i));
            newPacks.add(pack);
            out.add(pack);
        }
        return out;
    }

    /**
     * **组内同产物相邻**（用户 2026-09-29 定）：每个组里同一产物的多条做法排在一起，不再被别的
     * 产物夹在中间。产物按**首次出现的顺序**排列，同一产物内部保持原相对顺序（原组的做法在前、
     * 追加的做法在后），因此轮循顺序可预期：黑床（羊毛线 → 木板线 → 交叉染色）→ 蓝床 → …
     *
     * <p>只重建**顺序真的会变**的集合（每产物只有一条的组一律原样保留）；源集合不被修改 —— 与
     * 合并本身同一原则，所以关掉开关立刻恢复原顺序。重建出的组是普通组（不是收纳格），残缺标记
     * 由 Stage 6b 统一重放。</p>
     */
    private static List<RecipeCollection> applyResultClustering(
            List<RecipeCollection> collections,
            RegistryAccess registryAccess,
            List<RecipeCollection> newPacks) {
        List<RecipeCollection> out = null;
        for (int i = 0; i < collections.size(); i++) {
            RecipeCollection collection = collections.get(i);
            List<RecipeHolder<?>> recipes = collection.getRecipes();
            List<RecipeHolder<?>> clustered = clusteredEntries(recipes, registryAccess);
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
    private static List<RecipeHolder<?>> clusteredEntries(
            List<RecipeHolder<?>> recipes, RegistryAccess registryAccess) {
        int size = recipes.size();
        if (size < 2) {
            return recipes;
        }
        java.util.Map<ResultKey, List<RecipeHolder<?>>> groups = new java.util.LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipes) {
            ResultKey key = ResultKey.of(holder, registryAccess);
            if (key != null) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(holder);
            }
        }
        if (groups.size() == size) {
            return recipes; // 每产物一条 → 已经是聚拢的
        }
        List<RecipeHolder<?>> out = new ArrayList<>(size);
        java.util.Set<ResultKey> emitted = new java.util.HashSet<>();
        for (RecipeHolder<?> holder : recipes) {
            ResultKey key = ResultKey.of(holder, registryAccess);
            if (key == null) {
                out.add(holder); // 产物解不出：不参与聚拢，留在原位
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
     * 产物键：**物品 + 组件**（**忽略数量**——用户 2026-09-28 定）。
     *
     * <p>数量归一化成 1 再比较（"1 个"与"4 个"算同一产物）；组件走
     * {@link ItemStack#isSameItemSameComponents}（同物品不同附魔/药水不合并）。
     * 哈希用 {@code Item + 组件表}（{@code PatchedDataComponentMap} 的 equals/hashCode
     * 是值语义）。</p>
     */
    private static final class ResultKey {
        private final ItemStack stack;

        private ResultKey(ItemStack stack) {
            this.stack = stack;
        }

        /** 配方产物键；产物为空 / 解算失败返回 {@code null}。 */
        static ResultKey of(RecipeHolder<?> holder, RegistryAccess registryAccess) {
            ItemStack result;
            try {
                result = holder.value().getResultItem(registryAccess);
            } catch (Exception e) {
                return null;
            }
            if (result == null || result.isEmpty()) {
                return null;
            }
            return new ResultKey(result.copyWithCount(1)); // 忽略数量
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ResultKey key)) return false;
            return ItemStack.isSameItemSameComponents(this.stack, key.stack);
        }

        @Override
        public int hashCode() {
            return 31 * this.stack.getItem().hashCode() + this.stack.getComponents().hashCode();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Stage 3: Pins sort (in-place — was Stage 2 before ungroup added)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Moves pinned collections to the front of the list.  Mutates the
     * list in-place — the caller's reference is updated.
     */
    public static void applyPins(List<RecipeCollection> collections) {
        if (collections.size() <= 1) return;

        List<RecipeCollection> snapshot = new ArrayList<>(collections);
        for (RecipeCollection coll : snapshot) {
            if (BetterRecipeBook.pinnedRecipeManager.isFullyPinned(coll)) {
                collections.remove(coll);
                collections.add(0, coll);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Stage 4: Partial sort (pin-aware)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Sorts collections with pinned recipes always at highest priority,
     * then fully-craftable, then partially-craftable, then uncraftable.
     *
     * <p>Pin priority is absolute: a pinned uncraftable recipe comes
     * before an unpinned craftable one.  Within each pin group, the
     * standard category ordering applies.
     *
     * @param hasPartialData whether partial-material data is available
     *                       (controls whether partial recipes get a
     *                       dedicated middle bucket vs grouped with
     *                       uncraftable)
     */
    public static List<RecipeCollection> applyPartialSort(
            List<RecipeCollection> collections,
            boolean hasPartialData) {

        // Phase 1: split by pin status
        List<RecipeCollection> pinnedCraftable = new ArrayList<>();
        List<RecipeCollection> pinnedPartial = new ArrayList<>();
        List<RecipeCollection> pinnedUncraftable = new ArrayList<>();
        List<RecipeCollection> unpinnedCraftable = new ArrayList<>();
        List<RecipeCollection> unpinnedPartial = new ArrayList<>();
        List<RecipeCollection> unpinnedUncraftable = new ArrayList<>();

        for (RecipeCollection c : collections) {
            boolean isPinned = BetterRecipeBook.pinnedRecipeManager.isFullyPinned(c);

            if (hasPartialData) {
                // Use EvenIfStale to prevent category flicker when sorting
                // runs across a generation boundary (tab switch, config change).
                CollectionCategory cat = PartialCraftingUtil.categorizeEvenIfStale(c);
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

        // Phase 2: assemble — pinned before unpinned in each category
        List<RecipeCollection> result = new ArrayList<>(collections.size());
        result.addAll(pinnedCraftable);
        result.addAll(pinnedPartial);
        result.addAll(pinnedUncraftable);
        result.addAll(unpinnedCraftable);
        result.addAll(unpinnedPartial);
        result.addAll(unpinnedUncraftable);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Stage 4: Filter toggle
    // ═══════════════════════════════════════════════════════════════

    /**
     * Removes collections that have no craftable (and, when partial marking
     * is enabled, no partially-craftable) recipes.  Returns a new list.
     * When the filter toggle is off, returns the original list unchanged.
     */
    public static List<RecipeCollection> applyFilterToggle(
            List<RecipeCollection> collections,
            boolean isFiltering) {
        if (!isFiltering) return collections;

        boolean hasPartial = BetterRecipeBook.ctx().config().partialMarkingEnabled;
        List<RecipeCollection> result = new ArrayList<>();
        for (RecipeCollection coll : collections) {
            boolean keep = hasPartial
                    ? coll.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(coll)
                    : coll.hasCraftable();
            if (keep) result.add(coll);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Generic overloads (PipelineCollection)
    // These work with both vanilla RecipeCollection (via
    // VanillaPipelineCollection adapter) and GenericRecipeBookCollection.
    // ═══════════════════════════════════════════════════════════════

    /**
     * Moves pinned collections to the front of the list.  Mutates the
     * list in-place.  Works with any {@link PipelineCollection}.
     */
    public static <T extends PipelineCollection> void applyPinsGeneric(List<T> collections) {
        if (collections.size() <= 1) return;

        List<T> snapshot = new ArrayList<>(collections);
        for (T coll : snapshot) {
            if (isFullyPinnedGeneric(coll)) {
                collections.remove(coll);
                collections.add(0, coll);
            }
        }
    }

    /**
     * Sorts collections with pinned recipes always at highest priority,
     * then fully-craftable, then partially-craftable, then uncraftable.
     *
     * <p>Pin priority is absolute: a pinned uncraftable recipe comes
     * before an unpinned craftable one.  Works with any
     * {@link PipelineCollection}.
     */
    public static <T extends PipelineCollection> List<T> applyPartialSortGeneric(
            List<T> collections) {

        List<T> pinnedCraftable = new ArrayList<>();
        List<T> pinnedPartial = new ArrayList<>();
        List<T> pinnedUncraftable = new ArrayList<>();
        List<T> unpinnedCraftable = new ArrayList<>();
        List<T> unpinnedPartial = new ArrayList<>();
        List<T> unpinnedUncraftable = new ArrayList<>();

        for (T c : collections) {
            boolean isPinned = isFullyPinnedGeneric(c);

            boolean craftable = c.hasAnyCraftable();
            boolean partial = c.hasAnyPartiallyCraftable();

            if (isPinned) {
                if (craftable) pinnedCraftable.add(c);
                else if (partial) pinnedPartial.add(c);
                else pinnedUncraftable.add(c);
            } else {
                if (craftable) unpinnedCraftable.add(c);
                else if (partial) unpinnedPartial.add(c);
                else unpinnedUncraftable.add(c);
            }
        }

        List<T> result = new ArrayList<>(collections.size());
        result.addAll(pinnedCraftable);
        result.addAll(pinnedPartial);
        result.addAll(pinnedUncraftable);
        result.addAll(unpinnedCraftable);
        result.addAll(unpinnedPartial);
        result.addAll(unpinnedUncraftable);
        return result;
    }

    /**
     * Removes collections that have no craftable (and, when partial marking
     * is enabled, no partially-craftable) recipes.  Returns a new list.
     * When the filter toggle is off, returns the original list unchanged.
     * Works with any {@link PipelineCollection}.
     */
    public static <T extends PipelineCollection> List<T> applyFilterToggleGeneric(
            List<T> collections,
            boolean isFiltering) {
        if (!isFiltering) return collections;

        boolean hasPartial = BetterRecipeBook.ctx().config().partialMarkingEnabled;
        List<T> result = new ArrayList<>();
        for (T coll : collections) {
            boolean keep = hasPartial
                    ? coll.hasAnyCraftable() || coll.hasAnyPartiallyCraftable()
                    : coll.hasAnyCraftable();
            if (keep) result.add(coll);
        }
        return result;
    }
    /** 泛型版"全 pin"判定：组内每个配方 id 都在 pin 集合中（等价
     *  PinnedRecipeManager.isFullyPinned 的 RecipeCollection 版语义）。 */
    private static <T extends PipelineCollection> boolean isFullyPinnedGeneric(T coll) {
        List<?> recipes = coll.getRecipes();
        if (recipes == null || recipes.isEmpty()) return false;
        for (Object r : recipes) {
            ResourceLocation id = recipeIdOf(r);
            if (id == null || !BetterRecipeBook.pinnedRecipeManager.pinned.contains(id)) return false;
        }
        return true;
    }

    /** 从配方对象提取 ResourceLocation id（RecipeHolder / GenericRecipe 兼容）。 */
    private static ResourceLocation recipeIdOf(Object recipe) {
        if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> h) return h.id();
        if (recipe instanceof com.alonie.brbe.generic.GenericRecipe g) return g.id();
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // Stage 6: Pin extraction (1.21.1 RecipeHolder 版)
    // ═══════════════════════════════════════════════════════════════

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
    /**
     * pin 剥离（含副本去重版）：{@code merge} 非空时——
     * <ul>
     *   <li>重打包组继承父集合的专用格血统 / 副本索引；</li>
     *   <li>副本条目**两边都脱离父组**（同一条 pin 配方同时出现在混合组的 pin 组与收纳格的
     *       pin 组）时合二为一：只保留**专用格血统**那一份（用户 2026-09-28 定）。</li>
     * </ul>
     */
    public static void applyPinCopyGroups(List<RecipeCollection> collections, MergeResult merge) {
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
            List<RecipeHolder<?>> pinned = new ArrayList<>();
            List<RecipeHolder<?>> rest = new ArrayList<>();
            for (RecipeHolder<?> holder : collection.getRecipes()) {
                if (holder != null && BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(holder)) {
                    pinned.add(holder);
                } else if (holder != null) {
                    rest.add(holder);
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
            if (merge != null) {
                merge.inherit(collection, restPack);
                merge.inherit(collection, pinPack);
            }
            restPacks.put(collection, restPack);
            pinPacks.add(pinPack);
            BrbeLogger.log("BRBE-PINS",
                    "pin-extract: {} recipes, {} pinned variants -> rest {} + pin-group {}",
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

        // 3b) 副本两边都脱离父组 → 合二为一（保留专用格那一份；去空后的组直接丢弃）
        if (merge != null && !pinPacks.isEmpty()) {
            List<RecipeCollection> deduped = new ArrayList<>(pinPacks.size());
            java.util.Map<ResourceLocation, List<RecipeCollection>> occurrences = new java.util.LinkedHashMap<>();
            for (RecipeCollection pack : pinPacks) {
                for (RecipeHolder<?> holder : pack.getRecipes()) {
                    if (holder.id() != null && merge.isCopy(pack, holder.id())) {
                        occurrences.computeIfAbsent(holder.id(), k -> new ArrayList<>()).add(pack);
                    }
                }
            }
            java.util.Map<RecipeCollection, java.util.Set<ResourceLocation>> remove = new java.util.IdentityHashMap<>();
            for (java.util.Map.Entry<ResourceLocation, List<RecipeCollection>> occurrence : occurrences.entrySet()) {
                List<RecipeCollection> holders = occurrence.getValue();
                if (holders.size() < 2) continue;
                RecipeCollection keep = null;
                for (RecipeCollection holder : holders) {
                    if (merge.isDedicated(holder)) { keep = holder; break; }
                }
                if (keep == null) keep = holders.get(0);
                for (RecipeCollection holder : holders) {
                    if (holder != keep) {
                        remove.computeIfAbsent(holder, k -> new java.util.HashSet<>()).add(occurrence.getKey());
                    }
                }
            }
            for (RecipeCollection pack : pinPacks) {
                java.util.Set<ResourceLocation> drop = remove.get(pack);
                if (drop == null) { deduped.add(pack); continue; }
                List<RecipeHolder<?>> kept = new ArrayList<>();
                for (RecipeHolder<?> holder : pack.getRecipes()) {
                    if (holder.id() == null || !drop.contains(holder.id())) kept.add(holder);
                }
                if (kept.isEmpty()) continue; // 整格消失
                RecipeCollection rebuilt = buildPack(kept, pack);
                merge.inherit(pack, rebuilt);
                PIN_COPIES.add(rebuilt);
                deduped.add(rebuilt);
            }
            pinPacks = deduped;
        }

        // 4) pin 组置顶（按原组遍历顺序，与 pin 置顶规则一致）
        if (!pinPacks.isEmpty()) {
            collections.addAll(0, pinPacks);
        }
    }

    /** 由一组变体构建重打包组（同原组语义：canCraft 按玩家物品栏重算）。
     *  1.21.1 无 selectRecipes（1.21.5+ 拆分），用构造 + canCraft 一体式。 */
    private static RecipeCollection buildPack(List<RecipeHolder<?>> entries,
                                              RecipeCollection template) {
        RecipeCollection pack = new RecipeCollection(template.registryAccess(), new ArrayList<>(entries));
        if (Minecraft.getInstance().player != null) {
            var player = Minecraft.getInstance().player;
            var recipeBook = player.getRecipeBook();
            var stacked = new StackedContents();
            getStackedContents(stacked);
            pack.canCraft(stacked, 2, 2, recipeBook);
            pack.updateKnownRecipes(recipeBook);
        }
        return pack;
    }

    /** 填充当前玩家真实物品栏（items+armor+offhand）至 StackedContents。 */
    private static void getStackedContents(StackedContents stacked) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        for (var stack : p.getInventory().items) {
            if (!stack.isEmpty()) stacked.accountStack(stack);
        }
        for (var stack : p.getInventory().armor) {
            if (!stack.isEmpty()) stacked.accountStack(stack);
        }
        var offhand = p.getInventory().offhand.get(0);
        if (offhand != null && !offhand.isEmpty()) stacked.accountStack(offhand);
    }
}
