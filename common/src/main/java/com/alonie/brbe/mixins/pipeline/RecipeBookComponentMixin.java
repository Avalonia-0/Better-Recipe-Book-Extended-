package com.alonie.brbe.mixins.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.BookStateCache;
import com.alonie.brbe.util.CollectionPipeline;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.PerfTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
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
 * Pipeline + cache mixin for {@code RecipeBookComponent.updateCollections()}.
 *
 * <p>Handles search text management, BookStateCache lookup, and the
 * deterministic pipeline stages.  This mixin owns the
 * {@code @Redirect page.updateCollections} — there must be only one.
 *
 * <p>Runs AFTER the data-marking mixin
 * ({@code incompletecrafting/RecipeBookComponentMixin}),
 * which handles partial-material injection via {@code @Redirect List.forEach}.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow @Final protected Minecraft minecraft;

    @Shadow protected EditBox searchBox;

    @Unique
    private String brbe$savedSearchText;

    @Unique
    private SearchQuery brbe$parsedQuery;

    @Unique
    private long brbe$lastSlotHash;

    // ---- Search text save / restore ----

    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void brbe$saveSearchText(boolean resetPageNumber, CallbackInfo ci) {
        brbe$savedSearchText = null;
        brbe$parsedQuery = null;

        // Clear cross-screen cache on tab switch / search / filter toggle
        if (resetPageNumber) {
            BookStateCache.clear();
        }

        if (searchBox == null) return;
        String text = searchBox.getValue();
        if (text == null || text.isEmpty()) return;

        SearchQuery query = SearchQuery.parse(text);
        if (query.isAdvanced()) {
            brbe$savedSearchText = text;
            brbe$parsedQuery = query;
            searchBox.setValue("");
        }
    }

    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void brbe$restoreSearchText(boolean resetPageNumber, CallbackInfo ci) {
        if (brbe$savedSearchText != null && searchBox != null) {
            searchBox.setValue(brbe$savedSearchText);
            brbe$savedSearchText = null;
            brbe$parsedQuery = null;
        }
    }

    // ---- Pipeline with BookStateCache ----

    @Redirect(method = "updateCollections",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void brbe$runPipeline(RecipeBookPage page, List<RecipeCollection> list,
                                   boolean resetPageNumber) {

        // Compute slot hash for cache key
        long slotHash = 0L;
        if (minecraft.player != null && minecraft.player.containerMenu != null) {
            slotHash = PartialCraftingUtil.slotHash(minecraft.player.containerMenu.slots);
        }

        // BookStateCache: check for cached results
        if (minecraft.screen != null) {
            List<RecipeCollection> cached = BookStateCache.get(
                    minecraft.screen.getClass(), slotHash, null);
            if (cached != null) {
                page.updateCollections(cached, resetPageNumber);
                brbe$lastSlotHash = slotHash;
                PerfTimer.logNextRenderFrame = true;
                return;
            }
        }

        // Stage 1: Advanced search filter
        if (brbe$parsedQuery != null && minecraft.level != null) {
            list = CollectionPipeline.applySearch(list, brbe$parsedQuery, minecraft.level);
        }

        // Stage 2: Ungroup split
        list = CollectionPipeline.applyUngroup(list);

        // Stage 3: Pins sort (in-place)
        CollectionPipeline.applyPins(list);

        // Stage 4: Partial sort
        {
            boolean shouldSort = BetterRecipeBook.config.partialCraftingEnabled
                    || BetterRecipeBook.config.partialMarkingEnabled;
            if (shouldSort) {
                boolean useFullSort = BetterRecipeBook.config.partialCraftingEnabled;
                boolean hasPartialData = BetterRecipeBook.config.partialMarkingEnabled;
                list = CollectionPipeline.applyPartialSort(list, useFullSort, hasPartialData);
            }
        }

        // Stage 5: Filter toggle
        list = CollectionPipeline.applyFilterToggle(list,
                BetterRecipeBook.config.partialMarkingEnabled
                        || BetterRecipeBook.config.partialCraftingEnabled);

        // Persist to BookStateCache
        if (minecraft.screen != null) {
            BookStateCache.put(minecraft.screen.getClass(), slotHash, list, null);
        }

        brbe$lastSlotHash = slotHash;
        page.updateCollections(list, resetPageNumber);
        PerfTimer.logNextRenderFrame = true;
    }
}
