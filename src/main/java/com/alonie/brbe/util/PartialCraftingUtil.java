package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class PartialCraftingUtil {
    private static final RecipeCollectionTagger<RecipeDisplayId> tagger = new RecipeCollectionTagger<>();

    /**
     * {@link SlotDisplay} → 解析结果的按 Level 记忆化缓存。
     *
     * <p>配方原料的 {@code resolveForStacks} 结果不随物品栏变化，而残缺标记在
     * 每次物品栏刷新时都会对全部配方重新求值——无缓存时每次刷新重复解析约
     * 2 万次原料槽（新物品拾取 + 开着配方书 = 卡顿）。键用显示树对象身份
     * （配方重建/重载会产生新对象，天然失效）；Level 卸载后随 WeakHashMap
     * 回收。</p>
     */
    private static final Map<Level, Map<SlotDisplay, List<ItemStack>>> displayResolutionCache =
            new WeakHashMap<>();

    private PartialCraftingUtil() {
    }

    /**
     * Force a full rebuild on the next {@code updateCollections} pass.
     * Call when config options affecting partial-craftable display change
     * (e.g. {@code partialMarkingEnabled} toggled).
     */
    public static void invalidateCaches() {
        tagger.clearAll();
        tagger.beginFiltering(false);
    }

    /**
     * Single point-of-control for the partial material marking feature.
     * All public methods check this before doing any work, so callers
     * never need to repeat the config gate.
     */
    private static boolean enabled() {
        return BetterRecipeBook.config.partialMarkingEnabled;
    }

    // ---- Generation / freshness (delegated to tagger) ----

    public static void beginFilteringUpdate(boolean active) {
        tagger.beginFiltering(active);
    }

    /** The player's offhand stack, or EMPTY.  The offhand counts as part of the
     *  regular search space (vanilla {@code Inventory.fillStackedContents} only
     *  covers the main item list, and container menus have no offhand slot). */
    public static ItemStack offhandStack() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        return mc.player.getInventory().getItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND);
    }

    /** 玩家真实物品栏槽位列表（items + armor，不含 offhand——offhand 由
     *  {@link #offhandStack()} 在各方法内单独计入；不含屏幕容器槽位、合成网格与
     *  carried）。pin 与 viewer 的配方状态必须基于真实物品栏：创造模式物品栏的
     *  容器槽位（合成网格）与 carried 可能来自创造标签（虚拟物品），会被错误地
     *  当作可用材料。 */
    private static NonNullList<Slot> realInventorySlots() {
        NonNullList<Slot> slots = NonNullList.create();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return slots;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            slots.add(new Slot(inv, i, 0, 0));
        }
        for (int i = Inventory.SLOT_BODY_ARMOR; i < Inventory.SLOT_OFFHAND; i++) {
            slots.add(new Slot(inv, i, 0, 0));
        }
        return slots;
    }

    /** 常规检索空间槽位（配方状态判定的**唯一**槽位来源）：玩家真实物品栏
     *  （items + armor）+ 打开容器菜单的合成网格（工作台/背包 2×2/熔炉
     *  input+fuel），**排除合成台/熔炉的结果栏**（刚合成的产物不算可用材料）。
     *  carried（拿起物品）与 offhand（副手）不属于槽位：carried 由
     *  {@code slotHash}/{@code prepareForViewer} 的参数传入，offhand 由
     *  {@link #offhandStack()} 在内部单独计入。craftable 判定（stacked）统一
     *  走 {@link #fillSearchSpaceStackedContents}。创造模式物品栏的容器菜单只
     *  含真实槽位（创造标签列表不是槽位，网格为玩家实际放入的物品），因此
     *  不会把创造标签物品计入。 */
    public static NonNullList<Slot> searchSpaceSlots() {
        NonNullList<Slot> slots = realInventorySlots();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return slots;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (!(menu instanceof RecipeBookMenu)) return slots;
        Container playerInventory = mc.player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container == playerInventory) continue;                 // 玩家槽位（已含）
            if (slot.container instanceof ResultContainer) continue;         // 合成结果栏
            if (menu instanceof AbstractFurnaceMenu furnace
                    && slot == furnace.getResultSlot()) continue;            // 熔炉结果栏
            slots.add(slot);
        }
        return slots;
    }

    /** Fill the search space's stacked contents (for craftability): the player's
     *  inventory plus the offhand slot plus the open crafting menu's craft grid —
     *  mirroring the recipe book's own search space. */
    public static void fillSearchSpaceStackedContents(StackedItemContents stacked) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getInventory().fillStackedContents(stacked);
        ItemStack offhand = offhandStack();
        if (!offhand.isEmpty()) {
            stacked.accountSimpleStack(offhand);
        }
        if (mc.player.containerMenu instanceof RecipeBookMenu rbm) {
            rbm.fillCraftSlotsStackedContents(stacked);
        }
    }

    /** Clear the collection's "checked in the current generation" mark so the
     *  next {@link #markPartialMaterials} call re-evaluates it.  Used by the
     *  pin overlays' live state refresh; every other collection is untouched. */
    public static void forceReevaluate(RecipeCollection collection) {
        if (collection == null) return;
        tagger.clearAll(collection);
    }

    public static boolean wasCheckedForPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.wasChecked(collection);
    }

    /**
     * Prepare a fresh viewer (BRBE R/U alternative-recipe group) collection for
     * partial-crafting display: mark partially-craftable recipes and inject
     * them into the craftable set, mirroring what the recipe book's
     * updateCollections does for its own collections.  Without this a fresh
     * viewer collection would show every partial recipe as plain
     * "uncraftable" (grey slot, no red overlay).
     */
    public static void prepareForViewer(RecipeCollection collection, NonNullList<Slot> slots, ItemStack carried) {
        if (!enabled()) return;
        Set<Item> inventoryItems = hashInventory(slots, -1, carried);
        // The BRBE R/U viewer is unaffected by partialOnlyWhenCarrying: it always
        // marks every partial recipe against the full inventory, so the query
        // overlay shows partial (missing-material) recipes even when the player
        // is not carrying anything (and the book itself hides them).
        Set<Item> markItems = inventoryItems;
        Map<Item, Integer> counts = new HashMap<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        if (carried != null && !carried.isEmpty()) {
            counts.merge(carried.getItem(), carried.getCount(), Integer::sum);
        }
        ItemStack offhand = offhandStack();
        if (!offhand.isEmpty()) {
            counts.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
        }
        markPartialMaterials(collection, inventoryItems, counts, markItems);
        if (hasPartialMaterials(collection)) {
            RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (isPartiallyCraftable(collection, entry.id())) {
                    accessor.brbe$getCraftable().add(entry.id());
                }
            }
        }
    }

    // ---- Inventory hashing (unchanged) ----

    public static long slotHash(NonNullList<Slot> slots) {
        return slotHash(slots, ItemStack.EMPTY);
    }

    /**
     * 包含鼠标拿起物品（carried）的哈希。拿起/放下物品会改变哈希，
     * 从而触发配方书重新标记（否则 cache 跳过导致标记不更新）。
     * 副手槽位同样纳入：副手变化也触发重新标记。
     */
    public static long slotHash(NonNullList<Slot> slots, ItemStack carried) {
        long h = 1;
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                h = 31 * h + (long)stack.getItem().hashCode();
                h = 31 * h + stack.getCount();
            }
        }
        if (carried != null && !carried.isEmpty()) {
            h = 31 * h + (long)carried.getItem().hashCode();
            h = 31 * h + carried.getCount();
        }
        ItemStack offhand = offhandStack();
        if (!offhand.isEmpty()) {
            h = 31 * h + (long)offhand.getItem().hashCode();
            h = 31 * h + offhand.getCount();
        }
        return h;
    }

    public static java.util.Set<Item> hashInventory(NonNullList<Slot> slots) {
        return hashInventory(slots, -1);
    }

    /**
     * Like {@link #hashInventory} but skips the slot at {@code excludeIndex}
     * (or no slot if {@code excludeIndex < 0}).  Used to exclude the recipe
     * result slot: the crafted product sits in the result slot and must not
     * count as an available material, otherwise a just-crafted product would
     * make every recipe needing it appear "partially craftable".
     */
    public static java.util.Set<Item> hashInventory(NonNullList<Slot> slots, int excludeIndex) {
        return hashInventory(slots, excludeIndex, ItemStack.EMPTY);
    }

    /**
     * 包含鼠标拿起物品（carried）的库存类型集。副手槽位也计入常规检索空间。
     */
    public static java.util.Set<Item> hashInventory(NonNullList<Slot> slots, int excludeIndex, ItemStack carried) {
        java.util.Set<Item> inventoryItems = new java.util.HashSet<>();
        for (Slot slot : slots) {
            if (slot.index == excludeIndex) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryItems.add(stack.getItem());
            }
        }
        if (carried != null && !carried.isEmpty()) {
            inventoryItems.add(carried.getItem());
        }
        ItemStack offhand = offhandStack();
        if (!offhand.isEmpty()) {
            inventoryItems.add(offhand.getItem());
        }
        return inventoryItems;
    }

    // ---- Marking ----

    /** 清空原料解析缓存（离开世界/重进时调用；同 Level 内注册表固定无需清理）。 */
    public static void clearDisplayResolutionCache() {
        displayResolutionCache.clear();
    }

    /**
     * 带记忆化的原料槽解析：同一 Level 内 SlotDisplay 树不可变，解析结果可复用。
     * 失败返回空列表（与调用方原 try-catch 行为一致）。
     */
    private static List<ItemStack> resolveDisplayStacks(Level level, SlotDisplay slot, ContextMap context) {
        Map<SlotDisplay, List<ItemStack>> perLevel =
                displayResolutionCache.computeIfAbsent(level, k -> new java.util.IdentityHashMap<>());
        List<ItemStack> cached = perLevel.get(slot);
        if (cached != null) {
            return cached;
        }
        try {
            List<ItemStack> resolved = slot.resolveForStacks(context);
            perLevel.put(slot, resolved);
            return resolved;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static boolean markPartialMaterials(RecipeCollection collection, NonNullList<Slot> slots) {
        return markPartialMaterials(collection, hashInventory(slots));
    }

    public static boolean markPartialMaterials(RecipeCollection collection, java.util.Set<Item> inventoryItems) {
        return markPartialMaterials(collection, inventoryItems, null);
    }

    public static boolean markPartialMaterials(RecipeCollection collection, java.util.Set<Item> inventoryItems,
                                               java.util.Map<Item, Integer> inventoryCounts) {
        return markPartialMaterials(collection, inventoryItems, inventoryCounts, inventoryItems);
    }

    /**
     * {@code matchItems} 单独控制"哪些配方会被检索为残缺"——与 {@code inventoryItems}
     * （材料齐全判定用）解耦。partialOnlyWhenCarrying 场景：matchItems 仅含
     * carried 类型（或空集 → 完全不标 partial），而 3×3 材料齐全判定仍用完整库存。
     */
    public static boolean markPartialMaterials(RecipeCollection collection, java.util.Set<Item> inventoryItems,
                                               java.util.Map<Item, Integer> inventoryCounts,
                                               java.util.Set<Item> matchItems) {
        return markPartialMaterials(collection, inventoryItems, inventoryCounts, matchItems, false);
    }

    /**
     * {@code twoByTwoInventory}：当前是否为 2×2 生存背包网格。仅当为 true 且
     * showAllRecipesInSurvival 关闭时才跳过 3×3 配方；工作台（3×3 网格）不受影响。
     */
    public static boolean markPartialMaterials(RecipeCollection collection, java.util.Set<Item> inventoryItems,
                                               java.util.Map<Item, Integer> inventoryCounts,
                                               java.util.Set<Item> matchItems,
                                               boolean twoByTwoInventory) {
        if (!enabled()) return false;
        if (tagger.wasChecked(collection)) return tagger.hasAnyTag(collection);
        tagger.markAsChecked(collection);
        boolean markedAny = false;
        Set<RecipeDisplayId> partialRecipes = new HashSet<>();
        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (collection.isCraftable(recipe.id())) {
                continue;
            }

            // 3×3 配方：2×2 生存网格放不下。
            // showAllRecipesInSurvival 关闭时，仅 2×2 背包网格不显示 3×3 配方——
            // 不标 partial，否则会被注入 craftable 而残留显示（出现空气占位按钮）；
            // 工作台（3×3 网格）的 3×3 残缺配方照常标记。
            if (needsLargerGrid(recipe.display())) {
                if (twoByTwoInventory && !BetterRecipeBook.config.showAllRecipesInSurvival) continue;
                // 材料齐全（类型+数量都够）→ 不标 partial（网格问题，由 incompatible 警告处理）。
                // 材料不足（缺类型或缺数量）→ 标 partial（确实缺材料）。
                boolean complete = inventoryCounts != null
                        ? hasAllIngredients(recipe, inventoryItems, inventoryCounts)
                        : hasAllIngredients(recipe, inventoryItems);
                if (complete) {
                    continue;
                }
            }

            if (recipe.craftingRequirements().map(requirements -> hasMatchingIngredientFast(requirements, matchItems)).orElse(false)
                    || hasMatchingDisplayIngredientFast(recipe.display(), matchItems)) {
                partialRecipes.add(recipe.id());
                markedAny = true;
            }
        }

        if (markedAny) {
            tagger.setAllTags(collection, partialRecipes);
        } else {
            tagger.clearTags(collection);
        }

        return markedAny;
    }

    public static void markPartialMaterial(RecipeCollection collection, RecipeDisplayId recipeDisplayId) {
        if (!enabled()) return;
        tagger.addTag(collection, recipeDisplayId);
        tagger.markAsChecked(collection);
    }

    /**
     * Removes a single recipe from the partial-materials set for a collection.
     * Used to undo over-aggressive marking (e.g. 3×3 recipes that can never be
     * crafted in the 2×2 survival-inventory grid).
     */
    public static void unmarkPartial(RecipeCollection collection, RecipeDisplayId id) {
        if (!enabled()) return;
        tagger.removeTag(collection, id);
    }

    // ---- Queries ----

    /**
     * Generation-aware query: true only if the recipe was marked as partial
     * in the <em>current</em> generation.  Safe for button rendering and
     * general use — never returns stale data.
     */
    public static boolean isPartiallyCraftable(RecipeCollection collection, RecipeDisplayId recipeDisplayId) {
        if (!enabled()) return false;
        return tagger.hasTag(collection, recipeDisplayId);
    }

    /**
     * Stale-data query: true even if the recipe was marked in a previous
     * generation.  Only Step 0 (undo-injection) and root-cause cleanup
     * should use this.
     */
    public static boolean isPartiallyCraftableEvenIfStale(RecipeCollection collection, RecipeDisplayId id) {
        if (!enabled()) return false;
        return tagger.hasTagEvenIfStale(collection, id);
    }

    /**
     * Generation-aware query: true only if the collection has partial
     * materials marked in the <em>current</em> generation.
     */
    public static boolean hasPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.hasAnyTag(collection);
    }

    /**
     * Stale-data query: true even if partial data is from a previous
     * generation.  Only Step 0 should use this — it needs to know what
     * was injected last cycle so it can undo those injections.
     */
    public static boolean hasPartialMaterialsEvenIfStale(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.hasAnyTagEvenIfStale(collection);
    }

    /**
     * Raw query — bypasses the {@link #enabled()} guard.
     * Only Step 0 (undo-injection) should use this when the feature has
     * been toggled OFF and the standard queries would return false.
     */
    public static boolean isPartiallyCraftableRaw(RecipeCollection collection, RecipeDisplayId id) {
        return tagger.hasTagEvenIfStale(collection, id);
    }

    /**
     * Raw query — bypasses the {@link #enabled()} guard.
     * Only Step 0 (undo-injection) should use this when the feature has
     * been toggled OFF and the standard queries would return false.
     */
    public static boolean hasPartialMaterialsRaw(RecipeCollection collection) {
        return tagger.hasAnyTagEvenIfStale(collection);
    }

    /**
     * Classifies a collection by iterating its recipes and checking each
     * against both {@link #isPartiallyCraftable} and the vanilla craftable
     * set.  This is the single source of truth for collection classification;
     * sort methods and button mixins should use this instead of inline loops.
     */
    public static CollectionCategory categorize(RecipeCollection c) {
        if (!enabled()) return CollectionCategory.UNASSIGNED;
        boolean truly = false, partial = false;
        for (RecipeDisplayEntry entry : c.getRecipes()) {
            RecipeDisplayId id = entry.id();
            if (isPartiallyCraftable(c, id)) {
                partial = true;
            } else if (c.isCraftable(id)) {
                truly = true;
            }
        }
        if (truly) return CollectionCategory.TRULY_CRAFTABLE;
        if (partial) return CollectionCategory.PARTIAL;
        return CollectionCategory.UNASSIGNED;
    }

    public static List<RecipeDisplayEntry> getPartiallyCraftableRecipes(RecipeCollection collection) {
        if (!enabled()) return Collections.emptyList();
        if (!tagger.hasAnyTag(collection)) {
            return Collections.emptyList();
        }

        List<RecipeDisplayEntry> recipes = new ArrayList<>();
        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (tagger.hasTag(collection, recipe.id())) {
                recipes.add(recipe);
            }
        }

        return recipes;
    }

    public static List<RecipeDisplayEntry> getSelectedRecipes(RecipeCollection collection, RecipeCollection.CraftableStatus status) {
        if (!enabled()) return collection.getSelectedRecipes(status);
        List<RecipeDisplayEntry> selectedRecipes = collection.getSelectedRecipes(status);
        if (status != RecipeCollection.CraftableStatus.CRAFTABLE) {
            return selectedRecipes;
        }

        if (!tagger.hasAnyTag(collection)) {
            return selectedRecipes;
        }

        List<RecipeDisplayEntry> combinedRecipes = new ArrayList<>(selectedRecipes);
        Set<RecipeDisplayId> existingIds = new HashSet<>();
        for (RecipeDisplayEntry recipe : selectedRecipes) {
            existingIds.add(recipe.id());
        }

        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (tagger.hasTag(collection, recipe.id()) && !existingIds.contains(recipe.id())) {
                combinedRecipes.add(recipe);
            }
        }

        return combinedRecipes;
    }

    // ---- 3×3 网格判定 ----

    /**
     * 配方是否需要 3×3 合成网格（2×2 生存背包网格放不下）。
     * 这类配方无论材料是否齐全，都不属于"缺少部分材料"——
     * 网格不够由 {@code IncompatibleCraftingUtil} 的警告处理。
     */
    public static boolean needsLargerGrid(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.width() > 2 || shaped.height() > 2;
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients().size() > 4;
        }
        return false;
    }

    // ---- Ingredient matching helpers (unchanged) ----

    private static boolean hasMatchingIngredient(List<Ingredient> ingredients, NonNullList<Slot> slots) {
        return hasMatchingIngredientFast(ingredients, hashInventory(slots));
    }

    /**
     * Pre-check (parity with 1.21.1 RecipePipeline): on the inventory screen
     * with showAllRecipesInSurvival on, vanilla {@code canCraft} rejects 3×3
     * recipes on the 2×2 grid even when all materials are present.  Re-evaluate
     * them against raw item types and elevate to the craftable set so 3×3 and
     * 2×2 recipes behave identically.  Any stale partial tag is cleared.
     */
    public static void elevateFullyCraftable3x3(RecipeCollection collection, java.util.Set<Item> inventoryItems) {
        elevateFullyCraftable3x3(collection, inventoryItems, null);
    }

    public static void elevateFullyCraftable3x3(RecipeCollection collection, java.util.Set<Item> inventoryItems,
                                                java.util.Map<Item, Integer> inventoryCounts) {
        RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (!needsLargerGrid(entry.display())) continue;
            RecipeDisplayId id = entry.id();
            if (collection.isCraftable(id)) continue;
            boolean complete = inventoryCounts != null
                    ? hasAllIngredients(entry, inventoryItems, inventoryCounts)
                    : hasAllIngredients(entry, inventoryItems);
            if (complete) {
                accessor.brbe$getCraftable().add(id);
                unmarkPartial(collection, id);
            }
        }
    }

    /** True if every ingredient is present in inventory (by type). */
    /**
     * Carried-as-special-slot elevation: when the player holds an item
     * (mouse carried), vanilla {@code isCraftable()} still evaluates only
     * the menu slots — so a recipe whose materials moved from a slot into
     * the hand would drop out of the craftable set even though the
     * materials are fully available.  Re-evaluate ALL recipes (not just
     * 3×3) against slots+carried and elevate material-complete ones, so
     * picking up an item never changes the recipe book (materials simply
     * moved to the special "hand" slot).
     */
    public static void elevateFullyCraftableWithCarried(RecipeCollection collection, java.util.Set<Item> inventoryItems,
                                                        java.util.Map<Item, Integer> inventoryCounts) {
        elevateFullyCraftableWithCarried(collection, inventoryItems, inventoryCounts, false);
    }

    /**
     * {@code twoByTwoInventory}：当前是否为 2×2 生存背包网格。仅当为 true 且
     * showAllRecipesInSurvival 关闭时才跳过 3×3 配方的提升；工作台（3×3 网格）不受影响。
     */
    public static void elevateFullyCraftableWithCarried(RecipeCollection collection, java.util.Set<Item> inventoryItems,
                                                        java.util.Map<Item, Integer> inventoryCounts,
                                                        boolean twoByTwoInventory) {
        RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            RecipeDisplayId id = entry.id();
            if (collection.isCraftable(id)) continue;
            // showAllRecipesInSurvival 关闭时，仅 2×2 背包网格不提升 3×3 配方；
            // 工作台（3×3 网格）的 3×3 配方照常提升。
            if (twoByTwoInventory && !BetterRecipeBook.config.showAllRecipesInSurvival && needsLargerGrid(entry.display())) continue;
            boolean complete = inventoryCounts != null
                    ? hasAllIngredients(entry, inventoryItems, inventoryCounts)
                    : hasAllIngredients(entry, inventoryItems);
            if (complete) {
                accessor.brbe$getCraftable().add(id);
                unmarkPartial(collection, id);
            }
        }
    }

    private static boolean hasAllIngredients(RecipeDisplayEntry recipe, java.util.Set<Item> inventoryItems) {
        return recipe.craftingRequirements().map(requirements -> {
            for (Ingredient ingredient : requirements) {
                if (ingredient.isEmpty()) continue;
                if (!ingredient.items().anyMatch(holder -> inventoryItems.contains(holder.value()))) {
                    return false;
                }
            }
            return true;
        }).orElse(false);
    }

    /**
     * Quantity-aware variant: true only if every ingredient slot has a match
     * in inventory AND the inventory holds enough of each item.  A recipe
     * needing 3 iron + 2 sticks with only 1 of each in inventory must NOT be
     * treated as material-complete (it is material-deficient → partial).
     * Mirrors {@code RecipeStateDiagnostic.predictState} counting.
     */
    private static boolean hasAllIngredients(RecipeDisplayEntry recipe, java.util.Set<Item> inventoryItems,
                                             java.util.Map<Item, Integer> inventoryCounts) {
        RecipeDisplay display = recipe.display();
        List<SlotDisplay> slotDisplays;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            slotDisplays = shaped.ingredients();
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            slotDisplays = shapeless.ingredients();
        } else {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);

        Map<Item, Integer> neededCounts = new HashMap<>();
        int totalSlots = 0;
        for (SlotDisplay slot : slotDisplays) {
            List<ItemStack> variants;
            try {
                variants = resolveDisplayStacks(mc.level, slot, context);
            } catch (Exception e) {
                continue;
            }
            boolean emptySlot = true;
            for (ItemStack candidate : variants) {
                if (!candidate.isEmpty()) { emptySlot = false; break; }
            }
            if (emptySlot) continue;
            totalSlots++;
            Item chosen = variants.stream()
                    .filter(s -> !s.isEmpty())
                    .map(ItemStack::getItem)
                    .filter(inventoryItems::contains)
                    .findFirst().orElse(null);
            if (chosen != null) {
                neededCounts.merge(chosen, 1, Integer::sum);
            }
        }

        if (totalSlots == 0) return false;

        // 数量不足：某物品在成分槽出现次数 > 库存数量
        for (Map.Entry<Item, Integer> e : neededCounts.entrySet()) {
            int available = inventoryCounts.getOrDefault(e.getKey(), 0);
            if (available < e.getValue()) {
                return false;
            }
        }

        // 所有成分槽都有匹配（每槽最多匹配一种物品）
        return neededCounts.values().stream().mapToInt(Integer::intValue).sum() == totalSlots;
    }

    private static boolean hasMatchingIngredientFast(List<Ingredient> ingredients, java.util.Set<Item> inventoryItems) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }

            if (ingredient.items().anyMatch(holder -> inventoryItems.contains(holder.value()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingDisplayIngredient(RecipeDisplay display, NonNullList<Slot> slots) {
        return hasMatchingDisplayIngredientFast(display, hashInventory(slots));
    }

    private static boolean hasMatchingDisplayIngredientFast(RecipeDisplay display, java.util.Set<Item> inventoryItems) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return hasMatchingSlotDisplayFast(shaped.ingredients(), inventoryItems);
        }

        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return hasMatchingSlotDisplayFast(shapeless.ingredients(), inventoryItems);
        }

        return false;
    }

    private static boolean hasMatchingSlotDisplay(List<SlotDisplay> ingredients, NonNullList<Slot> slots) {
        return hasMatchingSlotDisplayFast(ingredients, hashInventory(slots));
    }

    private static boolean hasMatchingSlotDisplayFast(List<SlotDisplay> ingredients, java.util.Set<Item> inventoryItems) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }

        ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
        for (SlotDisplay ingredient : ingredients) {
            for (ItemStack candidate : resolveDisplayStacks(minecraft.level, ingredient, context)) {
                if (candidate.isEmpty()) {
                    continue;
                }

                if (inventoryItems.contains(candidate.getItem())) {
                    return true;
                }
            }
        }

        return false;
    }
}
