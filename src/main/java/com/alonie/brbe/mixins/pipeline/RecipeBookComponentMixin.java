package com.alonie.brbe.mixins.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.CollectionPipeline;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified pipeline for {@code RecipeBookComponent.updateCollections()}.
 *
 * <p>Replaces the four previously-scattered {@code @ModifyArg}/ {@code @Redirect}
 * handlers (search, ungroup, pins, incompletecrafting-sort) with a single
 * deterministic pipeline.  Pipeline order is defined in
 * {@link CollectionPipeline} and is:
 * <ol>
 *   <li>Advanced search filter</li>
 *   <li>Ungroup split (noGrouped)</li>
 *   <li>Pins sort (pinned → front)</li>
 *   <li>Partial sort (craftable → partial → uncraftable)</li>
 * </ol>
 *
 * <p>Also owns the search-text save/restore injects (moved from the
 * search package mixin, which is now retired).
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow @Final protected Minecraft minecraft;

    @Shadow protected EditBox searchBox;

    @SuppressWarnings("rawtypes")
    @Shadow
    protected RecipeBookMenu menu;

    @Unique
    private String brbe$savedSearchText;

    @Unique
    private SearchQuery brbe$parsedQuery;

    // ---- Pipeline output cache ----
    // When the inventory is unchanged (RecipeCraftingIndex.inventoryUnchanged),
    // every collection's canCraft/craftable state is identical to the last
    // pass, so pins order + partial sort produce the same list.  Reusing the
    // cached list skips the two O(collections) stages (applyPins + partial
    // sort's categorize) — the dominant cost of every recipe-book open on
    // large recipe packs.  Invalidated on inventory change, pin set change
    // (PinnedRecipeManager.version), search query change, config change, or
    // collection rebuild (RecipeCraftingIndex.generation).

    @Unique
    private List<RecipeCollection> brbe$cachedPipelinedList;

    @Unique
    private int brbe$cacheGeneration = -1;

    @Unique
    private int brbe$cachePinVersion = -1;

    @Unique
    private boolean brbe$cacheSearchActive;

    @Unique
    private boolean brbe$cacheConfigKey;

    @Unique
    private boolean brbe$cacheHasPipelined;

    @Unique
    private boolean brbe$configKey() {
        if (BetterRecipeBook.config == null) return false;
        return BetterRecipeBook.config.partialCraftingEnabled
                || BetterRecipeBook.config.partialMarkingEnabled
                || BetterRecipeBook.config.alternativeRecipes.noGrouped;
    }

    // ---- Search text save / restore ----

    /**
     * 右键点击搜索框时清空搜索文字、取消聚焦并刷新。
     *
     * <p>刷新用非重置模式（{@code resetPageNumber=false}）：清空搜索不把页码
     * 打回第 1 页；随后恢复该标签搜索前的浏览页码（"保存浏览记录"功能）。</p>
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$rightClickClearSearch(MouseButtonEvent event, boolean doubled,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 1 || searchBox == null) return;
        if (!searchBox.isMouseOver(event.x(), event.y())) return;
        searchBox.setValue("");
        searchBox.setFocused(false);
        ((RecipeBookComponentAccessor) this).updateCollectionsInvoker(false, false);
        brbe$restorePageAfterSearchClear();
        cir.setReturnValue(true);
    }

    /**
     * 搜索词清空后恢复该标签搜索前的浏览页码：页码来自记忆中的 basePage
     * （空搜索状态下持续更新的页码），钳制到当前列表范围。
     */
    @Unique
    private void brbe$restorePageAfterSearchClear() {
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) this;
        RecipeBookTabButton tab = acc.getSelectedTab();
        if (tab == null) return;
        int tabIndex = acc.getTabButtons().indexOf(tab);
        if (tabIndex < 0) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), tabIndex);
        if (pos == null) return;
        RecipeBookPage page = acc.getRecipeBookPage();
        RecipeBookPageAccessor pageAcc = (RecipeBookPageAccessor) page;
        int max = Math.max(0, pageAcc.getTotalPages() - 1);
        pageAcc.setCurrentPage(Math.min(pos.basePage(), max));
        pageAcc.updateButtonsForPageInvoker();
    }

    /**
     * Stage 0a: At HEAD, detect advanced search syntax.
     * If found, save and clear the search box so vanilla's substring
     * filter becomes a no-op.
     */
    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void brbe$saveSearchText(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        brbe$savedSearchText = null;
        brbe$parsedQuery = null;

        if (searchBox == null) return;

        String text = searchBox.getValue();
        if (text == null || text.isEmpty()) return;

        SearchQuery query = SearchQuery.parse(text);
        brbe$savedSearchText = text;
        brbe$parsedQuery = query;
        searchBox.setValue("");
    }

    /**
     * Stage 0b: At TAIL, restore the search box text if we cleared it.
     */
    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void brbe$restoreSearchText(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        if (brbe$savedSearchText != null && searchBox != null) {
            searchBox.setValue(brbe$savedSearchText);
            brbe$savedSearchText = null;
            brbe$parsedQuery = null;
        }
    }

    // ---- Config-change reload ----

    /**
     * Config-change-driven recipe book reload — equivalent to reopening
     * the recipe book.
     *
     * <p>Vanilla {@code tick()} only calls {@code updateStackedContents} →
     * {@code updateCollections} when the inventory changes.  If a config
     * toggle flips while the player is looking at the recipe book, the
     * change would go unnoticed until the next inventory change.  This hook
     * detects pending config changes and proactively calls
     * {@code updateStackedContents()}, which runs the full three-step
     * refresh: clear+refill stackedContents → selectMatchingRecipes (clears
     * and repopulates craftable sets) → updateCollections (filter+sort+pipeline).
     * The {@code keepPartiallyCraftable} redirect consumes the config-change
     * flag during this call and performs a full re-marking pass.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void brbe$reloadOnConfigChange(CallbackInfo ci) {
        if (BetterRecipeBook.ctx() == null) return;
        if (!BetterRecipeBook.ctx().events().hasPendingConfigChange()) return;
        // Only trigger when the recipe book is actually visible.
        // Otherwise the next open will trigger initVisuals()->updateCollections()
        // which naturally rebuilds everything.
        if (!((RecipeBookComponent)(Object)this).isVisible()) return;
        // Full refresh path: updateStackedContents triggers
        // selectMatchingRecipes → updateCollections pipeline.
        ((RecipeBookComponentAccessor)this).updateStackedContentsInvoker();
    }

    // ---- Pipeline ----

    /**
     * Replaces the {@code page.updateCollections(list, …)} call with the
     * deterministic pipeline.  Each stage is a pure function defined in
     * {@link CollectionPipeline}.
     */
    @Redirect(method = "updateCollections",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"))
    private void brbe$runPipeline(RecipeBookPage page, List<RecipeCollection> list,
                                   boolean resetPageNumber, boolean isFiltering) {

        // ---- Pipeline output cache ----
        // Inventory unchanged → canCraft state identical to last pass →
        // pins order + partial sort produce the same list.  Reuse it.
        boolean cacheHit = false;
        if (brbe$cacheHasPipelined
                && com.alonie.brbe.util.RecipeCraftingIndex.inventoryUnchanged()
                && brbe$cacheGeneration == com.alonie.brbe.util.RecipeCraftingIndex.generation()
                && brbe$cachePinVersion == BetterRecipeBook.pinnedRecipeManager.version()
                && brbe$cacheSearchActive == (brbe$parsedQuery != null)
                && brbe$cacheConfigKey == brbe$configKey()
                && !resetPageNumber) {
            // 缓存的是管线输出**原样快照**（浅拷贝）：Stage 6 会原地改写传入列表
            // （移除原组/插入重打包组），若直接复用同一对象，缓存里就只残留重打包
            // 组，下一次命中时原组无处还原。每次取出拷贝后由 Stage 6 重建。
            list = new ArrayList<>(brbe$cachedPipelinedList);
            cacheHit = true;
        }

        if (!cacheHit) {
            // Stage 1: Advanced search filter
            if (brbe$parsedQuery != null && minecraft.level != null) {
                list = CollectionPipeline.applySearch(
                        list, brbe$parsedQuery,
                        SlotDisplayContext.fromLevel(minecraft.level));
            }

            // Stage 2: Ungroup split (if noGrouped enabled)
            list = CollectionPipeline.applyUngroup(list);

            // Stage 3: Pins sort (in-place — moves pinned to front)
            CollectionPipeline.applyPins(list);

            // Stage 4: Craftable-before-partial sort (pin-aware).
            //
            // Two modes (spec §2.10):
            //   Default mode  (partialCraftingEnabled=false): filter button
            //     visible — sort only when isFiltering=true.
            //   Alternative   (partialCraftingEnabled=true):  filter button
            //     hidden  — always sort (craftable → partial → uncraftable).
            {
                boolean filterButtonHidden = BetterRecipeBook.config.partialCraftingEnabled;
                boolean shouldSort = filterButtonHidden || isFiltering;
                if (shouldSort) {
                    boolean hasPartialData = BetterRecipeBook.config.partialMarkingEnabled;
                    list = CollectionPipeline.applyPartialSort(list, true, hasPartialData);
                }
            }

            brbe$cachedPipelinedList = new ArrayList<>(list);
            brbe$cacheGeneration = com.alonie.brbe.util.RecipeCraftingIndex.generation();
            brbe$cachePinVersion = BetterRecipeBook.pinnedRecipeManager.version();
            brbe$cacheSearchActive = brbe$parsedQuery != null;
            brbe$cacheConfigKey = brbe$configKey();
            brbe$cacheHasPipelined = true;
        }

        // Stage 6: pin 剥离（pin 变体从原组取出 → 置顶；原组原位保留未 pin 变体）。
        // 幂等——无论全新管线输出还是缓存快照副本，本阶段都先清旧重打包组再重建。
        CollectionPipeline.applyPinCopyGroups(list);

        // Stage 6b：pin 提取生成的新组（rest/pin 包）是全新 RecipeCollection 对象，
        // 而残缺标记/注入按"集合对象身份"记录（tagger 弱键）——新对象没有标记 →
        // 组内残缺配方退化为不可合成。重新走一遍「标记 → carried 提升 → 注入
        // craftable」（与 incompletecrafting 主 passes 同参数）；已检查过的原组
        // wasChecked 自动跳过（无副作用），仅未检查的重打包组真正生效。
        brbe$reapplyPartialMarking(list);

        page.updateCollections(list, resetPageNumber, isFiltering);
    }

    /**
     * 对最终管线列表重放残缺标记（Stage 6b，见调用处注释）。
     * 计算参数与 incompletecrafting/RecipeBookComponentMixin 主 passes 完全一致；
     * markPartialMaterials 内部以 wasChecked 幂等——原组跳过，新组重新标记；
     * 标记后按主 passes 顺序：carried/offhand 提升 → 残缺 ID 注入 craftable 集合
     * （注入后 isCraftable() 为 true，红罩渲染依赖它 + tagger 标记）。
     */
    @Unique
    private void brbe$reapplyPartialMarking(List<RecipeCollection> collections) {
        if (collections == null || collections.isEmpty()) return;
        boolean onInventoryScreen = this.minecraft != null
                && this.minecraft.gui.screen() instanceof InventoryScreen;
        ItemStack carried = this.menu != null
                ? this.menu.getCarried() : ItemStack.EMPTY;
        Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(
                PartialCraftingUtil.searchSpaceSlots(), -1, carried);
        // Item → 总数量（数量感知的材料齐全判定，与主 passes 同源）。
        Map<Item, Integer> inventoryCounts = new HashMap<>();
        for (net.minecraft.world.inventory.Slot slot : PartialCraftingUtil.searchSpaceSlots()) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        if (!carried.isEmpty()) {
            inventoryCounts.merge(carried.getItem(), carried.getCount(), Integer::sum);
        }
        ItemStack offhand = PartialCraftingUtil.offhandStack();
        if (!offhand.isEmpty()) {
            inventoryCounts.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
        }
        boolean partialOnlyWhenCarrying = BetterRecipeBook.config.partialOnlyWhenCarrying;
        Set<Item> markItems = partialOnlyWhenCarrying
                ? (carried.isEmpty() ? Set.of() : Set.of(carried.getItem()))
                : inventoryItems;

        for (RecipeCollection collection : collections) {
            PartialCraftingUtil.markPartialMaterials(
                    collection, inventoryItems, inventoryCounts, markItems, onInventoryScreen);
            if (!carried.isEmpty() || !offhand.isEmpty()) {
                PartialCraftingUtil.elevateFullyCraftableWithCarried(
                        collection, inventoryItems, inventoryCounts, onInventoryScreen);
            }
            if (onInventoryScreen && BetterRecipeBook.config.showAllRecipesInSurvival) {
                PartialCraftingUtil.elevateFullyCraftable3x3(
                        collection, inventoryItems, inventoryCounts);
            }
            if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        accessor.brbe$getCraftable().add(id);
                    }
                }
            }
        }
    }

    @Unique
    private boolean configKey() {
        if (BetterRecipeBook.config == null) return false;
        return BetterRecipeBook.config.partialCraftingEnabled
                || BetterRecipeBook.config.partialMarkingEnabled
                || BetterRecipeBook.config.alternativeRecipes.noGrouped;
    }

    @Unique
    private String bookKey() {
        String type = menu != null ? menu.getRecipeBookType().name() : "";
        String screen = menu != null ? menu.getClass().getSimpleName() : "";
        return "vanilla:" + type + ":" + screen;
    }
}
