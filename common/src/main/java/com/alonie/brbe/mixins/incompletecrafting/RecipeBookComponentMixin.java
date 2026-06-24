package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Unified mixin handling all {@code updateCollections} processing for the
 * vanilla {@link RecipeBookComponent}.
 *
 * <h3>Execution order</h3>
 * <ol>
 *   <li>{@code @Inject HEAD} — timing + save advanced search text</li>
 *   <li>{@code @Redirect List.forEach} — cache check; on miss: partial
 *       marking + incompatible marking + vanilla forEach</li>
 *   <li>Vanilla: substring search &amp; craftable filter (work correctly on
 *       cache miss; results replaced by cache on hit)</li>
 *   <li>{@code @Redirect page.updateCollections} — on hit: restore cached
 *       list; on miss: {@link CollectionPipeline} → cache save</li>
 *   <li>{@code @Inject TAIL} — restore search text</li>
 * </ol>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow @Final
    protected RecipeBookMenu<?, ?> menu;

    @Shadow @Final
    protected Minecraft minecraft;

    @Shadow
    protected EditBox searchBox;

    // ── State fields ──

    @Unique
    private long brbe$lastSlotHash;

    @Unique
    private String brbe$savedSearchText;

    @Unique
    private SearchQuery brbe$parsedQuery;

    @Unique
    private List<RecipeCollection> brbe$cachedList;

    @Unique
    private boolean brbe$cacheWasHit;

    // ══════════════════════════════════════════════════════════
    // Timing injects
    // ══════════════════════════════════════════════════════════

    @Unique
    private static long brbe$cycleStartNanos;

    @Inject(method = "initVisuals", at = @At("HEAD"))
    private void brbe$initVisualsStart(CallbackInfo ci) {
        brbe$cycleStartNanos = System.nanoTime();
        BetterRecipeBook.LOGGER.info("[BRBE-Timing] initVisuals START");
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$renderStart(GuiGraphics g, int mx, int my, float d, CallbackInfo ci) {
        if (PerfTimer.logNextRenderFrame) {
            BetterRecipeBook.LOGGER.info("[BRBE-Timing] render START (+{}ms from initVisuals)",
                    (System.nanoTime() - brbe$cycleStartNanos) / 1_000_000);
            PerfTimer.begin();
            PerfTimer.start("render.frame");
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void brbe$renderEnd(GuiGraphics g, int mx, int my, float d, CallbackInfo ci) {
        if (PerfTimer.logNextRenderFrame) {
            PerfTimer.end("render.frame");
            PerfTimer.logAndReset("render (first frame after update)");
            BetterRecipeBook.LOGGER.info("[BRBE-Timing] render END (+{}ms from initVisuals, render={}µs)",
                    (System.nanoTime() - brbe$cycleStartNanos) / 1_000_000,
                    (System.nanoTime() - brbe$cycleStartNanos) / 1_000);
            PerfTimer.logNextRenderFrame = false;
        }
    }

    // ══════════════════════════════════════════════════════════
    // Stage 1: HEAD — timing + save advanced search text
    // ══════════════════════════════════════════════════════════

    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void brbe$updateCollectionsHead(boolean resetPage, CallbackInfo ci) {
        BetterRecipeBook.LOGGER.info("[BRBE-Timing] updateCollections START (+{}ms from initVisuals)",
                (System.nanoTime() - brbe$cycleStartNanos) / 1_000_000);

        // Clear cross-screen cache on tab switch / search / filter toggle
        // to prevent stale data from a different tab from being served.
        if (resetPage) {
            BookStateCache.clear();
        }

        brbe$savedSearchText = null;
        brbe$parsedQuery = null;

        if (searchBox != null) {
            String text = searchBox.getValue();
            if (text != null && !text.isEmpty()) {
                SearchQuery query = SearchQuery.parse(text);
                if (query.isAdvanced()) {
                    brbe$savedSearchText = text;
                    brbe$parsedQuery = query;
                    searchBox.setValue("");
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // Stage 2: REDIRECT List.forEach — cache check + data marking
    // ══════════════════════════════════════════════════════════

    @Redirect(method = "updateCollections",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void brbe$keepPartiallyCraftable(
            List<RecipeCollection> collections,
            Consumer<? super RecipeCollection> vanillaConsumer) {

        long slotHash = PartialCraftingUtil.slotHash(menu.slots);
        boolean inventoryChanged = (slotHash != brbe$lastSlotHash);

        Object rbipVariant = brbe$rbipVariant();
        if (brbe$tryCacheHit(collections, slotHash, rbipVariant)) return;

        brbe$cacheWasHit = false;
        brbe$cachedList = null;

        boolean onInventoryScreen = minecraft.screen instanceof InventoryScreen;
        boolean retainPartial = BetterRecipeBook.config.partialMarkingEnabled;
        boolean retainIncompatible = onInventoryScreen
                && BetterRecipeBook.config.showAllRecipesInSurvival;
        boolean filter3x3 = !BetterRecipeBook.config.showAllRecipesInSurvival;

        // Step 0: Remove previously-injected partials from craftable sets
        if (retainPartial && (inventoryChanged || retainIncompatible)) {
            brbe$clearInjectedPartials(collections, filter3x3);
        }

        // Step 1: Run vanilla forEach (marks full craftability)
        collections.forEach(vanillaConsumer);

        // Build reverse index once (used by RecipeIndex for partial-mark optimization)
        if (!RecipeIndex.isBuilt() && minecraft.player != null) {
            RecipeIndex.build(minecraft.player.getRecipeBook().getCollections());
        }

        // Step 2: Partial material marking & injection
        // Must run whenever Step 0 ran (i.e., when partials were cleared),
        // not only on inventory change. Otherwise partial markings are lost
        // after cache invalidation until the next inventory change.
        if (retainPartial && (inventoryChanged || retainIncompatible)) {
            Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(menu.slots);
            brbe$markAndInjectPartials(collections, filter3x3, inventoryItems);
            PartialCraftingUtil.clearCategoryCache();
        }

        // Step 3: Incompatible recipe marking
        if (retainIncompatible) {
            IncompatibleCraftingUtil.beginFiltering(true);
            for (RecipeCollection coll : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(coll);
            }
        }

        // Step 4: Update slot hash
        if (inventoryChanged) {
            brbe$lastSlotHash = slotHash;
        }
    }

    // ══════════════════════════════════════════════════════════
    // Stage 3: REDIRECT page.updateCollections — restore cache
    //           or run CollectionPipeline on vanilla-filtered list
    // ══════════════════════════════════════════════════════════

    @Redirect(method = "updateCollections",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void brbe$runPipeline(RecipeBookPage page, List<RecipeCollection> list,
                                              boolean resetPageNumber) {

        if (brbe$cacheWasHit) {
            page.updateCollections(brbe$cachedList, resetPageNumber);
            brbe$cachedList = null;
            brbe$cacheWasHit = false;
            PerfTimer.logNextRenderFrame = true;
            return;
        }

        // Run CollectionPipeline on the vanilla-filtered list
        if (brbe$parsedQuery != null && minecraft.level != null) {
            list = CollectionPipeline.applySearch(list, brbe$parsedQuery, minecraft.level);
        }
        CollectionPipeline.applyPins(list);

        boolean isFiltering = minecraft.player != null
                && minecraft.player.getRecipeBook().isFiltering(menu);
        boolean shouldSort = BetterRecipeBook.config.partialCraftingEnabled
                || (BetterRecipeBook.config.partialMarkingEnabled && isFiltering);
        if (shouldSort) {
            boolean useFullSort = BetterRecipeBook.config.partialCraftingEnabled || isFiltering;
            boolean hasPartialData = BetterRecipeBook.config.partialMarkingEnabled;
            list = CollectionPipeline.applyPartialSort(list, useFullSort, hasPartialData);
        }
        list = CollectionPipeline.applyFilterToggle(list, isFiltering);

        brbe$cacheSave(list);
        page.updateCollections(list, resetPageNumber);
        PerfTimer.logNextRenderFrame = true;
    }

    // ══════════════════════════════════════════════════════════
    // Stage 4: TAIL — restore search text
    // ══════════════════════════════════════════════════════════

    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void brbe$restoreSearchText(boolean resetPage, CallbackInfo ci) {
        if (brbe$savedSearchText != null && searchBox != null) {
            searchBox.setValue(brbe$savedSearchText);
            brbe$savedSearchText = null;
            brbe$parsedQuery = null;
        }
    }

    // ══════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════

    /** Derives the RBIP creative-tab variant from the currently selected tab. */
    @Unique
    private Object brbe$rbipVariant() {
        var accessor = (RecipeBookComponentAccessor) this;
        var selectedTab = accessor.getSelectedTab();
        if (selectedTab != null
                && selectedTab.getCategory() == net.minecraft.client.RecipeBookCategories.UNKNOWN) {
            return com.alonie.recipebookispain_extended.RecipeBookIsPain.activeCreativeTab;
        }
        return null;
    }

    /** Checks BookStateCache; on hit sets brbe$cachedList/brbe$cacheWasHit and returns true. */
    @Unique
    private boolean brbe$tryCacheHit(List<RecipeCollection> collections, long slotHash, Object variant) {
        if (minecraft.screen == null) return false;
        Class<?> screenClass = minecraft.screen.getClass();
        List<RecipeCollection> cached = BookStateCache.get(screenClass, slotHash, variant);
        if (cached == null) {
            BetterRecipeBook.LOGGER.info("[BRBE-Diag] CACHE-MISS {} hash={} variant={}",
                    screenClass.getSimpleName(), slotHash,
                    variant == null ? "none" : "rbip");
            return false;
        }
        BetterRecipeBook.LOGGER.info("[BRBE-Diag] CACHE-HIT {} hash={} variant={} {}colls",
                screenClass.getSimpleName(), slotHash,
                variant == null ? "none" : "rbip", cached.size());
        brbe$cachedList = cached;
        brbe$cacheWasHit = true;
        return true;
    }

    /** Step 0: Removes previously-injected partial recipes from the craftable set. */
    @Unique
    private static void brbe$clearInjectedPartials(List<RecipeCollection> collections, boolean filter3x3) {
        for (RecipeCollection coll : collections) {
            if (!PartialCraftingUtil.hasPartialMaterials(coll)) continue;
            RecipeCollectionAccessor ca = (RecipeCollectionAccessor) coll;
            for (RecipeHolder<?> holder : coll.getRecipes()) {
                if (PartialCraftingUtil.isPartiallyCraftable(coll, holder)
                        && !(filter3x3 && brbe$needsLargerGrid(holder.value()))) {
                    ca.brbe$getCraftable().remove(holder);
                }
            }
        }
    }

    /** Step 2: Marks partially-craftable materials and injects them into the craftable set. */
    @Unique
    private void brbe$markAndInjectPartials(List<RecipeCollection> collections,
                                             boolean filter3x3, Set<Item> inventoryItems) {
        PartialCraftingUtil.beginFilteringUpdate(true);

        // Use reverse index to only check collections that use items the player has
        Iterable<RecipeCollection> targets = collections;
        if (RecipeIndex.isBuilt()) {
            Set<RecipeCollection> indexed = RecipeIndex.getAffected(inventoryItems);
            if (indexed.size() < collections.size() / 2) {
                targets = indexed;
            }
        }

        for (RecipeCollection coll : targets) {
            PartialCraftingUtil.markPartialMaterials(coll, inventoryItems);
            if (!PartialCraftingUtil.hasPartialMaterials(coll)) continue;
            RecipeCollectionAccessor ca = (RecipeCollectionAccessor) coll;
            for (RecipeHolder<?> holder : coll.getRecipes()) {
                if (PartialCraftingUtil.isPartiallyCraftable(coll, holder)
                        && !(filter3x3 && brbe$needsLargerGrid(holder.value()))) {
                    ca.brbe$getCraftable().add(holder);
                }
            }
        }

        // Purge 3×3 recipes from partial set to prevent a degradation loop:
        // markPartialMaterials tags them, injection skips them, Step 0 would
        // then remove vanilla's craftable marking on the next call.
        if (filter3x3) {
            for (RecipeCollection coll : collections) {
                if (!PartialCraftingUtil.hasPartialMaterials(coll)) continue;
                for (RecipeHolder<?> holder : coll.getRecipes()) {
                    if (brbe$needsLargerGrid(holder.value())
                            && PartialCraftingUtil.isPartiallyCraftable(coll, holder)) {
                        PartialCraftingUtil.unmarkPartial(coll, holder.id());
                    }
                }
            }
        }

        PartialCraftingUtil.beginFilteringUpdate(false);
    }

    /** Persists the pipeline result to BookStateCache. */
    @Unique
    private void brbe$cacheSave(List<RecipeCollection> list) {
        if (minecraft.screen == null || list.isEmpty()) return;
        Object variant = brbe$rbipVariant();
        BookStateCache.put(minecraft.screen.getClass(), brbe$lastSlotHash, list, variant);
        BetterRecipeBook.LOGGER.info("[BRBE-Pipeline] SAVE {} (hash={}, variant={}, {} colls)",
                minecraft.screen.getClass().getSimpleName(), brbe$lastSlotHash,
                variant == null ? "none" : "rbip", list.size());
    }

    /** True if a recipe needs more than a 2×2 crafting grid. */
    @Unique
    private static boolean brbe$needsLargerGrid(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getWidth() > 2 || shaped.getHeight() > 2;
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.getIngredients().size() > 4;
        }
        return false;
    }
}
