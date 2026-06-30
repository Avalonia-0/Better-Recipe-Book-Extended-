package com.alonie.brbe.mixins.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.BrbeLogger;
import com.alonie.brbe.util.CollectionPipeline;
import com.alonie.brbe.util.CollectionCategory;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.VanillaPipelineCollection;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements RecipeBookComponentAccessor {

    @Shadow @Final protected Minecraft minecraft;
    @Shadow @Final protected RecipeBookMenu<?, ?> menu;
    @Shadow private ClientRecipeBook book;
    @Shadow @Final private RecipeBookPage recipeBookPage;

    /**
     * Set to {@code true} before a config-change-triggered
     * {@code initVisuals()} call so that the TAIL inject knows to
     * preserve the current page instead of resetting it.
     */
    @Unique
    private boolean brbe$configChangeRefresh = false;

    // ═══════════════════════════════════════════════════════════════
    // Config-change refresh (HEAD of render)
    // ═══════════════════════════════════════════════════════════════

    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$refreshOnConfigChange(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // F8 hotkey: dump diagnostic to game dir
        if (com.alonie.brbe.BetterRecipeBook.DIAGNOSTIC_MAPPING.consumeClick()) {
            com.alonie.brbe.util.BrbeDiagnostic.dump();
        }

        if (!AppContext.instance().events().consumeConfigChange()) return;
        if (!this.getVisible()) return;

        BrbeLogger.log(BrbeLogger.Category.RENDER, "configChanged — full rebuild");

        // Reset vanilla filter.
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.getRecipeBook().setFiltering(
                    menu.getRecipeBookType(), false);
        }

        // Request that the next updateCollections call forces a full
        // rebuild (vanilla forEach + partial marking).  Must be set
        // BEFORE initVisuals because initVisuals calls updateTabs() →
        // updateCollections() internally, and the @Redirect needs to
        // see the flag during that call.
        PartialCraftingUtil.requestForceFullRefresh();

        // Rebuild UI.  The TAIL inject on initVisuals handles
        // populating the page (partial marking + craftable injection
        // + sort + page.updateCollections) — no other calls needed.
        // Signal the TAIL inject to preserve the current page.
        brbe$configChangeRefresh = true;
        this.initVisualsInvoker();
        brbe$configChangeRefresh = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // Populate page after initVisuals (handles re-open + config)
    // ═══════════════════════════════════════════════════════════════

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$populateAfterInitVisuals(CallbackInfo ci) {
        BrbeLogger.log(BrbeLogger.Category.STATE, "initVisuals TAIL");
        populatePage(!brbe$configChangeRefresh);
    }

    /**
     * Sort collections, populate fitsDimensions, and push to the page.
     * Called from the TAIL of {@code initVisuals()} every time it runs.
     *
     * <p><b>Craftable-set management is handled by the
     * {@code @Redirect} in {@code incompletecrafting/RecipeBookComponentMixin}
     * which runs during {@code initVisuals → updateTabs → updateCollections}.</b>
     * This method only sorts and pushes — it does NOT touch the
     * craftable set or partial markings.  For config changes, the
     * render hook sets {@code requestForceFullRefresh()} before
     * {@code initVisuals()} so the Redirect forces a full rebuild.
     *
     * @param resetPage true when initVisuals is a fresh start (re-open,
     *                  config change); false for mid-session updates.
     */
    private void populatePage(boolean resetPage) {
        var tab = this.getSelectedTab();
        if (tab == null) return;

        List<RecipeCollection> collections =
                new ArrayList<>(book.getCollection(tab.getCategory()));

        if (collections.isEmpty()) return;

        BrbeLogger.log(BrbeLogger.Category.STATE,
                "populatePage — %d collections, reset=%s", collections.size(), resetPage);

        // Sort uses PARTIAL_RECIPES + craftable sets from the @Redirect
        // that already ran during initVisuals → updateTabs → updateCollections.
        sort(collections);

        // Ensure every collection has fitsDimensions populated.
        // On the crafting table (3×3 grid) this is always safe because all
        // recipes fit the grid.  On the inventory screen (2×2 grid) we only
        // fill fitsDimensions when showAllRecipesInSurvival is enabled,
        // because vanilla correctly leaves 3×3-only collections empty there.
        boolean onInventory = minecraft != null && minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        boolean skipFallback = onInventory && !BetterRecipeBook.ctx().config().showAllRecipesInSurvival;
        if (!skipFallback) {
            for (RecipeCollection c : collections) {
                var ca = (RecipeCollectionAccessor) c;
                if (ca.getFitsDimensions().isEmpty()) {
                    ca.getFitsDimensions().addAll(c.getRecipes());
                }
            }
        }

        // When showAllRecipesInSurvival is OFF and on the inventory screen,
        // purge 3×3 recipes from fitsDimensions first (they may have leaked
        // in from a crafting-table visit where the slot-hash cache skipped
        // canCraft), then remove collections that end up empty.
        if (onInventory && !BetterRecipeBook.ctx().config().showAllRecipesInSurvival) {
            for (RecipeCollection c : collections) {
                var ca = (RecipeCollectionAccessor) c;
                java.util.Set<RecipeHolder<?>> fits = ca.getFitsDimensions();
                if (!fits.isEmpty()) {
                    fits.removeIf(r -> !brbe$fitsInventoryGrid(r));
                }
            }
            collections.removeIf(c -> {
                var ca = (RecipeCollectionAccessor) c;
                return ca.getFitsDimensions().isEmpty();
            });
        }

        BrbeLogger.log(BrbeLogger.Category.STATE,
                "populatePage — recipeBookPage=%s, calling updateCollections",
                recipeBookPage != null ? "ok" : "NULL");

        recipeBookPage.updateCollections(collections, resetPage);

        // Verify: check how many buttons the page actually has after.
        var buttons = ((RecipeBookPageAccessor)(Object) recipeBookPage).getButtons();
        int visible = 0;
        for (var b : buttons) {
            if (b.visible) visible++;
        }
        BrbeLogger.log(BrbeLogger.Category.STATE,
                "populatePage — after push: %d visible buttons", visible);
    }

    /**
     * Returns {@code true} if the recipe fits the 2×2 inventory crafting grid.
     */
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

    // ═══════════════════════════════════════════════════════════════
    // @ModifyArg (normal path)
    // ═══════════════════════════════════════════════════════════════

    @ModifyArg(method = "updateCollections",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"),
               index = 0)
    private List<RecipeCollection> brbe$applySortPipeline(List<RecipeCollection> collections) {
        sort(collections);
        return collections;
    }

    // ═══════════════════════════════════════════════════════════════
    // Sort
    // ═══════════════════════════════════════════════════════════════

    /** Delegate sorting to CollectionPipeline — thin adapter pattern. */
    private void sort(List<RecipeCollection> collections) {
        if (collections == null || collections.isEmpty()) return;
        // Pin sort (in-place, moves pinned to front)
        if (BetterRecipeBook.ctx().config().enablePinning) {
            CollectionPipeline.applyPins(collections);
        }
        // Partial sort (6-bucket craftable/partial/uncraftable × pinned/unpinned)
        boolean hasPartial = BetterRecipeBook.ctx().config().partialCraftingEnabled
                && BetterRecipeBook.ctx().config().partialMarkingEnabled;
        boolean isFiltering = minecraft != null && minecraft.player != null
                && minecraft.player.getRecipeBook().isFiltering(menu);
        if (BetterRecipeBook.ctx().config().partialCraftingEnabled || isFiltering) {
            List<RecipeCollection> sorted = CollectionPipeline.applyPartialSort(collections, hasPartial);
            collections.clear();
            collections.addAll(sorted);
        }
    }
}
