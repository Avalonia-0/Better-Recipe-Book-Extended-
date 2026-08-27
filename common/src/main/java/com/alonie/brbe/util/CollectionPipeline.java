package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.generic.pins.PipelineCollection;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * {@code alternativeRecipes.noGrouped} is enabled.  Returns a new list.
     */
    public static List<RecipeCollection> applyUngroup(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.ctx().config().alternativeRecipes.noGrouped) {
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
