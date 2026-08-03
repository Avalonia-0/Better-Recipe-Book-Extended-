package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.util.*;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
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
import java.util.Set;
import java.util.function.Consumer;

/**
 * Thin adapter between vanilla {@link RecipeBookComponent} and
 * {@link RecipePipeline}.
 *
 * <h3>Injection points</h3>
 * <ol>
 *   <li>{@code @Redirect List.forEach} — after vanilla {@code canCraft},
 *       calls {@link RecipePipeline#updateRecipeState} to fix 3×3 recipes
 *       and mark partial/incompatible state.</li>
 *   <li>{@code @Redirect page.updateCollections} — before page update,
 *       calls {@link RecipePipeline#prepareDisplay} to filter visibility
 *       and sort for display.  (Does NOT re-run state computation.)</li>
 *   <li>{@code @Inject initVisuals TAIL} — on tab switch / first open,
 *       calls both {@code updateRecipeState} (defensive) and
 *       {@code prepareDisplay} on fresh collections from the recipe book.</li>
 * </ol>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements RecipeBookComponentAccessor {

    @Shadow @Final protected RecipeBookMenu<?, ?> menu;
    @Shadow @Final protected Minecraft minecraft;
    @Shadow private ClientRecipeBook book;
    @Shadow @Final private RecipeBookPage recipeBookPage;
    @Shadow private String lastSearch;

    @Unique
    private long brbe$lastSlotHash;

    // Detect when recipe collections have been rebuilt (new objects).
    // On the crafting table, an external canCraft call path can clear
    // the craftable set + partial tags between ticks.  Tracking the
    // identity hash of a stable collection lets us detect rebuilds
    // without running canCraft every tick.
    @Unique
    private int brbe$lastFirstCollIdHash;

    // =================================================================
    // @Redirect List.forEach — vanilla canCraft + BRBE state update
    // =================================================================

    @Redirect(method = "updateCollections",
              at = @At(value = "INVOKE",
                       target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void brbe$forEachRedirect(List<RecipeCollection> collections,
                                       Consumer<? super RecipeCollection> vanillaConsumer) {

        PerfTimer.begin();

        long slotHash = PartialCraftingUtil.slotHash(menu.slots);
        boolean forceRefresh = PartialCraftingUtil.consumeForceFullRefresh();
        boolean inventoryChanged = slotHash != brbe$lastSlotHash || forceRefresh;
        boolean onInventory = minecraft != null
                && minecraft.screen instanceof EffectRenderingInventoryScreen;

        if (forceRefresh) {
            BrbeLogger.log(BrbeLogger.Category.PIPELINE,
                    "forceRefresh consumed, hashChanged=%s",
                    slotHash != brbe$lastSlotHash);
        }

        // On the crafting table, detect when recipe collections have been
        // rebuilt (new RecipeCollection objects).  An external canCraft
        // call path can clear the craftable set + partial tags between
        // ticks.  By comparing identity hashes we detect rebuilds without
        // running canCraft every tick.
        int firstCollHash = (!onInventory && !collections.isEmpty())
                ? System.identityHashCode(collections.get(0)) : 0;
        boolean rebuildDetected = firstCollHash != 0
                && firstCollHash != brbe$lastFirstCollIdHash;

        if (inventoryChanged || rebuildDetected) {
            PerfTimer.start("vanilla.forEach");
            collections.forEach(vanillaConsumer);
            PerfTimer.end("vanilla.forEach");
            brbe$lastSlotHash = slotHash;
            brbe$lastFirstCollIdHash = firstCollHash;
        }

        // Diagnostic: count craftable state before pipeline
        int diagCraftableBefore = 0;
        int diagKnown = 0;
        for (RecipeCollection c : collections) {
            if (c.hasCraftable()) diagCraftableBefore++;
            if (c.hasKnownRecipes()) diagKnown++;
        }
        com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                "[BRBE-DIAG] forEach: total={} known={} craftable={}",
                collections.size(), diagKnown, diagCraftableBefore);

        Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(menu.slots);

        boolean needsMarkAndInject = inventoryChanged || rebuildDetected
                || forceRefresh;

        PipelineContext ctx = PipelineContext.builder()
                .inventoryItems(inventoryItems)
                .menuSlots(menu.slots)
                .onInventoryScreen(onInventory)
                // On the crafting table, also run markAndInject when
                // rebuild detected so partial recipes are restored.
                .partialMarkingEnabled(BetterRecipeBook.ctx().config().partialMarkingEnabled)
                .brbeSortEnabled(BetterRecipeBook.ctx().config().partialCraftingEnabled)
                .showAllRecipesInSurvival(BetterRecipeBook.ctx().config().showAllRecipesInSurvival)
                .isFiltering(false)
                // On the crafting table, always treat inventory as changed
                // so that markAndInject runs after canCraft to restore
                // partial recipes that canCraft removed.
                .inventoryChanged(needsMarkAndInject)
                .pinnedManager(BetterRecipeBook.pinnedRecipeManager)
                .build();

        PerfTimer.start("updateRecipeState");
        RecipePipeline.updateRecipeState(collections, ctx);
        PerfTimer.end("updateRecipeState");

        // On the crafting table (3×3), vanilla's updateCollections calls
        // list2.removeIf(c -> c.getFitsDimensions().isEmpty()) after
        // canCraft.  After shift-click crafting, canCraft leaves
        // fitsDimensions nearly empty (ingredients consumed), so vanilla
        // removes almost all collections → the recipe book appears empty.
        // Repopulate fitsDimensions HERE (before vanilla's removeIf)
        // so the collections survive vanilla's filtering.
        if (!onInventory) {
            for (RecipeCollection c : collections) {
                var ca = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) c;
                ca.getFitsDimensions().clear();
                ca.getFitsDimensions().addAll(c.getRecipes());
            }
        }

        // Diagnostic: count craftable state after pipeline
        int diagCraftableAfter = 0;
        int diagPartialAfter = 0;
        for (RecipeCollection c : collections) {
            if (c.hasCraftable()) diagCraftableAfter++;
            if (PartialCraftingUtil.hasPartialMaterials(c)) diagPartialAfter++;
        }
        com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                "[BRBE-DIAG] forEachRedirect: invChanged={} onInv={} "
                + "totalColls={} craftableBefore={} craftableAfter={} partialAfter={}",
                inventoryChanged, onInventory, collections.size(),
                diagCraftableBefore, diagCraftableAfter, diagPartialAfter);

        // Diagnostic: log when entering forEach with snapshot of list size.
        // Paired with pageUpdateRedirect to detect if vanilla empties list2.
        if (collections != null && collections.size() < 50) {
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] forEachRedirect: small list size={} "
                    + "onInventory={} invChanged={}",
                    collections.size(), onInventory, inventoryChanged);
        }

        PerfTimer.logAndReset("updateCollections");
    }

    // =================================================================
    // @Redirect page.updateCollections — visibility + sorting only
    // =================================================================

    @Redirect(method = "updateCollections",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void brbe$pageUpdateRedirect(RecipeBookPage page,
                                          List<RecipeCollection> list,
                                          boolean resetPageNumber) {

        boolean onInventory = minecraft != null
                && minecraft.screen instanceof EffectRenderingInventoryScreen;
        boolean partialCrafting = BetterRecipeBook.ctx().config().partialCraftingEnabled;
        boolean partialMarking = BetterRecipeBook.ctx().config().partialMarkingEnabled;

        // Diagnostic: category + known count of the list after vanilla removeIf
        int knownAfter = 0;
        if (list != null) {
            for (RecipeCollection c : list) if (c.hasKnownRecipes()) knownAfter++;
        }
        com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                "[BRBE-DIAG] pageUpdateRedirect: listSize={} known={} onInv={} tab={}",
                list == null ? -1 : list.size(), knownAfter, onInventory,
                this.getSelectedTab() != null
                        ? this.getSelectedTab().getCategory() : "null");

        if (list == null || list.isEmpty()) {
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] pageUpdateRedirect: vanilla passed EMPTY list! "
                    + "onInventory={} pCE={} pME={} isFiltering={} lastSearch='{}'",
                    onInventory, partialCrafting, partialMarking,
                    minecraft != null && minecraft.player != null
                            && minecraft.player.getRecipeBook().isFiltering(menu),
                    lastSearch);
            page.updateCollections(list, resetPageNumber);
            return;
        }

        // When partialCrafting is enabled, ignore vanilla's filter toggle.
        // DisableCraftableFilter hides the button but vanilla may still
        // leave isFiltering=true, which would cause applyFilterToggle to
        // drop all non-craftable collections after crafting consumes items.
        boolean isFiltering = !partialCrafting
                && minecraft != null && minecraft.player != null
                && minecraft.player.getRecipeBook().isFiltering(menu);

        PipelineContext ctx = PipelineContext.builder()
                .inventoryItems(PartialCraftingUtil.hashInventory(menu.slots))
                .menuSlots(menu.slots)
                .onInventoryScreen(onInventory)
                .partialMarkingEnabled(partialMarking)
                .brbeSortEnabled(partialCrafting)
                .showAllRecipesInSurvival(BetterRecipeBook.ctx().config().showAllRecipesInSurvival)
                .isFiltering(isFiltering)
                .inventoryChanged(true)
                .pinnedManager(BetterRecipeBook.pinnedRecipeManager)
                .build();

        // Only visibility + sorting here — state was already updated
        // by brbe$forEachRedirect earlier in the same updateCollections call.
        List<RecipeCollection> result = RecipePipeline.prepareDisplay(list, ctx);

        if (result.isEmpty()) {
            BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] pageUpdateRedirect: prepareDisplay returned EMPTY! "
                    + "inputSize={} onInventory={} isFiltering={} noGrouped={}",
                    list.size(), onInventory, isFiltering,
                    BetterRecipeBook.ctx().config().alternativeRecipes.noGrouped);
        }

        page.updateCollections(result, resetPageNumber);

        // Post-check: verify the page actually got our list
        var pageAccessor = (com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor) page;
        List<RecipeCollection> stored = pageAccessor.getCollections();
        if (stored != null && stored.isEmpty() && !result.isEmpty()) {
            BetterRecipeBook.LOGGER.warn(
                    "[BRBE-DIAG] pageUpdateRedirect: vanilla page.updateCollections "
                    + "EMPTIED our list! input={} stored=0", result.size());
        }
    }

    // =================================================================
    // @Inject tick RETURN — config-change refresh
    // =================================================================

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

    // =================================================================
    // @Inject initVisuals TAIL — display prep only (state already updated)
    // =================================================================
    //
    // updateRecipeState already ran via @Redirect List.forEach inside
    // the updateCollections() call that initVisuals makes.  Calling it
    // again here causes a second markPartialMaterials pass which sees
    // isCraftable=true (from the first pass's injection) and calls
    // clearTags() — wiping out the correct partial data.

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$resetPageAfterInitVisuals(CallbackInfo ci) {
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

        boolean onInventory = minecraft != null
                && minecraft.screen instanceof EffectRenderingInventoryScreen;
        boolean partialCrafting = BetterRecipeBook.ctx().config().partialCraftingEnabled;
        boolean partialMarking = BetterRecipeBook.ctx().config().partialMarkingEnabled;

        // Ignore vanilla filter toggle when BRBE manages recipe state
        // (same rationale as brbe$pageUpdateRedirect above).
        boolean isFiltering = !partialCrafting
                && minecraft != null && minecraft.player != null
                && minecraft.player.getRecipeBook().isFiltering(menu);

        PipelineContext ctx = PipelineContext.builder()
                .inventoryItems(PartialCraftingUtil.hashInventory(menu.slots))
                .menuSlots(menu.slots)
                .onInventoryScreen(onInventory)
                .partialMarkingEnabled(partialMarking)
                .brbeSortEnabled(partialCrafting)
                .showAllRecipesInSurvival(BetterRecipeBook.ctx().config().showAllRecipesInSurvival)
                .isFiltering(isFiltering)
                .inventoryChanged(true)
                .pinnedManager(BetterRecipeBook.pinnedRecipeManager)
                .build();

        BrbeLogger.log(BrbeLogger.Category.STATE,
                "initVisuals TAIL — %d collections", collections.size());

        // State already updated by updateRecipeState via @Redirect
        // List.forEach in updateCollections().  Only prepare display here.
        List<RecipeCollection> result = RecipePipeline.prepareDisplay(collections, ctx);
        recipeBookPage.updateCollections(result, true);
    }
}
