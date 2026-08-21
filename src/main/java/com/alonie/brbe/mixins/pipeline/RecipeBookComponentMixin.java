package com.alonie.brbe.mixins.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.CollectionPipeline;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

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

        // Note: Filter toggle is handled by the incompletecrafting mixin's
        // removeIf redirect, which injects partially-craftable recipes into
        // the craftable set so they survive the vanilla filter.  The pipeline
        // is responsible only for sorting, not filtering.
        page.updateCollections(list, resetPageNumber, isFiltering);
    }

    @Unique
    private String bookKey() {
        String type = menu != null ? menu.getRecipeBookType().name() : "";
        String screen = menu != null ? menu.getClass().getSimpleName() : "";
        return "vanilla:" + type + ":" + screen;
    }
}
