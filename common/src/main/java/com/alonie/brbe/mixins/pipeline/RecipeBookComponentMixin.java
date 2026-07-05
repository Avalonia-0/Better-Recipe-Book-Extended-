package com.alonie.brbe.mixins.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.BrbeLogger;
import com.alonie.brbe.util.CollectionPipeline;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified pipeline for 1.21.1 {@link RecipeBookComponent}.
 *
 * <p>Two injection points share a single pipeline method:
 * <ol>
 *   <li>{@code @Redirect} on {@code page.updateCollections} — sorts every
 *       list that vanilla passes to the page (tick-to-tick updates).</li>
 *   <li>{@code @Inject TAIL} on {@code initVisuals} — after the recipe book
 *       opens or config changes, fetches the collections for the current tab
 *       and pushes them through the pipeline with a page reset.</li>
 * </ol>
 * Both paths call {@link #brbe$runPipeline(RecipeBookPage, List, boolean)}
 * so sorting, fitsDimensions, and 3×3 filtering are always consistent.</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements RecipeBookComponentAccessor {

    @Shadow @Final protected Minecraft minecraft;
    @Shadow @Final protected RecipeBookMenu<?, ?> menu;
    @Shadow private ClientRecipeBook book;
    @Shadow @Final private RecipeBookPage recipeBookPage;

    // ═══════════════════════════════════════════════════════════════
    // Config-change refresh — tick() RETURN
    // ═══════════════════════════════════════════════════════════════

    @Inject(method = "tick", at = @At("RETURN"))
    private void brbe$refreshOnConfigChange(CallbackInfo ci) {
        if (com.alonie.brbe.BetterRecipeBook.DIAGNOSTIC_MAPPING.consumeClick()) {
            com.alonie.brbe.util.BrbeDiagnostic.dump();
        }

        if (!AppContext.instance().events().consumeConfigChange()) return;
        if (!this.getVisible()) return;

        BrbeLogger.log(BrbeLogger.Category.RENDER, "configChanged — tick rebuild");

        if (minecraft != null && minecraft.player != null) {
            minecraft.player.getRecipeBook().setFiltering(
                    menu.getRecipeBookType(), false);
        }

        this.updateStackedContentsInvoker();
        PartialCraftingUtil.requestForceFullRefresh();
        this.initVisualsInvoker();
    }

    // ═══════════════════════════════════════════════════════════════
    // TAIL inject — reset page after initVisuals
    // ═══════════════════════════════════════════════════════════════

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$resetPageAfterInitVisuals(CallbackInfo ci) {
        // When partialCraftingEnabled removes the filter button, ensure
        // the internal filter state is OFF so uncraftable recipes appear.
        // Otherwise a stale filter=true from a previous session hides them.
        if (BetterRecipeBook.ctx().config().partialCraftingEnabled
                && minecraft != null && minecraft.player != null) {
            minecraft.player.getRecipeBook().setFiltering(
                    menu.getRecipeBookType(), false);
        }

        var tab = this.getSelectedTab();
        if (tab == null) return;

        List<RecipeCollection> collections =
                new ArrayList<>(book.getCollection(tab.getCategory()));

        if (collections.isEmpty()) return;

        BrbeLogger.log(BrbeLogger.Category.STATE,
                "initVisuals TAIL — %d collections, running pipeline", collections.size());

        // Run pipeline and push with page reset.
        // FitsDimensions + 3x3 filtering are applied inside runPipeline.
        brbe$runPipeline(recipeBookPage, collections, true);
    }

    // ═══════════════════════════════════════════════════════════════
    // @Redirect — pipeline on every page.updateCollections call
    // ═══════════════════════════════════════════════════════════════

    @Redirect(method = "updateCollections",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void brbe$runPipelineRedirect(RecipeBookPage page, List<RecipeCollection> list,
                                           boolean resetPageNumber) {
        brbe$runPipeline(page, list, resetPageNumber);
    }

    // ═══════════════════════════════════════════════════════════════
    // Pipeline — shared by both injection points
    // ═══════════════════════════════════════════════════════════════

    @Unique
    private void brbe$runPipeline(RecipeBookPage page, List<RecipeCollection> list,
                                   boolean resetPageNumber) {

        if (list == null || list.isEmpty()) {
            page.updateCollections(list, resetPageNumber);
            return;
        }

        boolean isFiltering = minecraft != null && minecraft.player != null
                && minecraft.player.getRecipeBook().isFiltering(menu);

        // -- Stage 1: Pins ----------------------------------------------
        CollectionPipeline.applyPins(list);

        // -- Stage 2: Partial sort -----------------------------------
        // shouldSort: always sort when the filter button is hidden
        // (partialCraftingEnabled) or when the user has toggled the
        // "only show craftable" filter.
        boolean shouldSort = BetterRecipeBook.ctx().config().partialCraftingEnabled
                || isFiltering;
        // hasPartialData: partial recipes exist in PARTIAL_RECIPES only
        // when partialMarkingEnabled (the red overlay) is on, because
        // markAndInject is gated by enabled() = partialMarkingEnabled.
        // partialCraftingEnabled alone does NOT create partial data.
        boolean hasPartialData = BetterRecipeBook.ctx().config().partialMarkingEnabled;
        if (shouldSort) {
            List<RecipeCollection> sorted = CollectionPipeline.applyPartialSort(
                    list, hasPartialData);
            list.clear();
            list.addAll(sorted);
        }

        // -- Stage 3: FitsDimensions fallback (crafting table) -----------
        boolean onInventory = minecraft != null && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        boolean skipFallback = onInventory && !BetterRecipeBook.ctx().config().showAllRecipesInSurvival;
        if (!skipFallback) {
            for (RecipeCollection c : list) {
                var ca = (RecipeCollectionAccessor) c;
                if (ca.getFitsDimensions().isEmpty()) {
                    ca.getFitsDimensions().addAll(c.getRecipes());
                }
            }
        }

        // -- Stage 4: Purge 3×3 recipes on inventory screen --------------
        if (onInventory && !BetterRecipeBook.ctx().config().showAllRecipesInSurvival) {
            for (RecipeCollection c : list) {
                var ca = (RecipeCollectionAccessor) c;
                java.util.Set<RecipeHolder<?>> fits = ca.getFitsDimensions();
                if (!fits.isEmpty()) {
                    fits.removeIf(r -> !brbe$fitsInventoryGrid(r));
                }
            }
            list.removeIf(c -> {
                var ca = (RecipeCollectionAccessor) c;
                return ca.getFitsDimensions().isEmpty();
            });
        }

        // -- Stage 5: Filter toggle ------------------------------------
        // Both paths need this: the @Redirect path's list was already
        // filtered by vanilla removeIf (so filtering is a no-op), but
        // the TAIL path works from raw book.getCollection() data that
        // has not been through updateCollections at all.
        list = CollectionPipeline.applyFilterToggle(list, isFiltering);

        page.updateCollections(list, resetPageNumber);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    @Unique
    private static boolean brbe$fitsInventoryGrid(RecipeHolder<?> holder) {
        var recipe = holder.value();
        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            return shaped.getWidth() <= 2 && shaped.getHeight() <= 2;
        }
        if (recipe instanceof net.minecraft.world.item.crafting.ShapelessRecipe shapeless) {
            return shapeless.getIngredients().size() <= 4;
        }
        return true;
    }
}
