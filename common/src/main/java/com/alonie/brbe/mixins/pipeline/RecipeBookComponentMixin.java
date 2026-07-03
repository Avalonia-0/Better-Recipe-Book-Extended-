package com.alonie.brbe.mixins.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.CollectionPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Unique
    private String brbe$savedSearchText;

    @Unique
    private SearchQuery brbe$parsedQuery;

    // ---- Search text save / restore ----

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
     * Config-change-driven recipe book reload.
     *
     * <p>Vanilla {@code tick()} only calls {@code updateStackedContents} →
     * {@code updateCollections} when the inventory changes.  If a config
     * toggle flips while the player is looking at the recipe book, the
     * change would go unnoticed until the next inventory change.  This hook
     * detects pending config changes and proactively calls
     * {@code updateCollections(false, false)} so the
     * {@code keepPartiallyCraftable} redirect can consume the flag and
     * perform a full re-marking pass — without resetting the page number.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void brbe$reloadOnConfigChange(CallbackInfo ci) {
        if (BetterRecipeBook.ctx() == null) return;
        if (!BetterRecipeBook.ctx().events().hasPendingConfigChange()) return;
        // Only trigger when the recipe book is actually visible.
        // Otherwise the next open will trigger initVisuals()->updateCollections(true, false)
        // which naturally rebuilds everything.
        if (!((RecipeBookComponent)(Object)this).isVisible()) return;
        // updateCollections(false, false): rebuild without page reset
        ((RecipeBookComponentAccessor)this).updateCollectionsInvoker(false, false);
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
}
