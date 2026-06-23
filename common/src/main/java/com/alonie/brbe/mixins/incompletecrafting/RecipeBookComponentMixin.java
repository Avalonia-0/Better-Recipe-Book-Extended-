package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.BookStateCache;
import com.alonie.brbe.util.CollectionCategory;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.PerfTimer;
import com.alonie.brbe.util.RecipeIndex;
import com.alonie.brbe.util.SlotTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
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
import java.util.Set;
import java.util.function.Consumer;

/**
 * After updateCollections() runs its internal forEach consumer (which populates
 * craftable + fitsDimensions sets), we inject additional recipes:
 *
 * 1. Partial recipes → craftable set (when partialCraftableEqualsCraftable)
 *    Prevents the craftable filter from removing collections with partial materials.
 *
 * 2. Incompatible (3x3) recipes → fitsDimensions set (when showAllRecipesInSurvival)
 *    Makes getRecipes(false) naturally return them for display and search.
 *
 * Both work at the data layer, so no removeIf/predicate patches are needed.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected RecipeBookMenu<?, ?> menu;

    @Shadow @Final
    protected Minecraft minecraft;

    private static long brbe$lastSlotHash;

    // Holds the final filtered/sorted list from the last sortBeforePageUpdate
    // redirect, so the TAIL inject can save it to BookStateCache.
    @Unique
    private List<RecipeCollection> brbe$lastPageList;

    /**
     * HEAD inject — checks BookStateCache before the vanilla updateCollections
     * pipeline runs.  If the cache has results for this screen type + slot hash,
     * restores them and cancels the entire vanilla method (including all our
     * redirects), saving ~150ms on the forEach + partial marking pass.
     */
    @Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$checkResultCache(boolean resetPage, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.screen == null) return;

        long slotHash = PartialCraftingUtil.slotHash(this.menu.slots);
        Class<?> screenClass = this.minecraft.screen.getClass();
        List<RecipeCollection> cached = BookStateCache.get(screenClass, slotHash);

        if (cached != null) {
            BetterRecipeBook.LOGGER.info("[BRBE-Cache] HIT {} (hash={}, {} colls)",
                    screenClass.getSimpleName(), slotHash, cached.size());
            RecipeBookPage page = ((RecipeBookComponentAccessor) (Object) this).getRecipeBookPage();
            page.updateCollections(cached, resetPage);
            brbe$lastSlotHash = slotHash;
            ci.cancel();
        }
    }

    /**
     * TAIL inject — after the pipeline completes, saves the final page results
     * to BookStateCache so the next screen reopen can skip the pipeline.
     */
    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void betterRecipeBook$saveResultCache(boolean resetPage, CallbackInfo ci) {
        if (brbe$lastPageList == null || brbe$lastPageList.isEmpty()) return;
        if (this.minecraft == null || this.minecraft.screen == null) return;

        Class<?> screenClass = this.minecraft.screen.getClass();
        BetterRecipeBook.LOGGER.info("[BRBE-Cache] SAVE {} (hash={}, {} colls)",
                screenClass.getSimpleName(), brbe$lastSlotHash, brbe$lastPageList.size());
        BookStateCache.put(screenClass, brbe$lastSlotHash, brbe$lastPageList);
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void betterRecipeBook$injectIntoDataSets(
            List<RecipeCollection> collections, Consumer<? super RecipeCollection> consumer) {
        PerfTimer.begin();

        // ── Phase D: detect changed items for incremental update ──
        long slotHash = PartialCraftingUtil.slotHash(this.menu.slots);
        boolean inventoryChanged = (slotHash != brbe$lastSlotHash);
        Class<?> menuClass = this.menu.getClass();
        Set<Item> changedItems = null;
        java.util.Set<RecipeCollection> dirtySet = null;
        boolean incremental = false;

        if (inventoryChanged) {
            changedItems = SlotTracker.changedItems(menuClass, this.menu.slots);

            if (changedItems != null && RecipeIndex.isBuilt()) {
                dirtySet = RecipeIndex.getAffected(changedItems);
                if (dirtySet.size() < collections.size() / 2) {
                    incremental = true;
                }
            }

            if (incremental) {
                PerfTimer.start("vanilla.forEach-incr");
                int processed = 0;
                for (RecipeCollection coll : collections) {
                    if (dirtySet.contains(coll)) {
                        PartialCraftingUtil.clearCategory(coll);
                        consumer.accept(coll);
                        processed++;
                    }
                }
                PerfTimer.end("vanilla.forEach-incr");
                BetterRecipeBook.LOGGER.info("[BRBE-Incr] dirty={}/{} items={}",
                        processed, collections.size(), changedItems.size());
            } else {
                PerfTimer.start("vanilla.forEach");
                collections.forEach(consumer);
                PerfTimer.end("vanilla.forEach");

                // Build the reverse index from all known collections
                if (!RecipeIndex.isBuilt() && this.minecraft != null
                        && this.minecraft.player != null) {
                    RecipeIndex.build(this.minecraft.player.getRecipeBook().getCollections());
                }
            }
            brbe$lastSlotHash = slotHash;
        }

        // ── Gate variables ──
        boolean onInventory = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        boolean retainPartial = BetterRecipeBook.config.partialMarkingEnabled;
        boolean retainIncompatible = onInventory
                && BetterRecipeBook.config.showAllRecipesInSurvival;

        if (!retainPartial && !retainIncompatible) {
            PerfTimer.logAndReset("updateCollections (no-op)");
            return;
        }

        // ── Incompatible recipe marking ──
        // Only needed when the collection list or inventory changed.
        // Result depends purely on recipe dimensions, so it's idempotent
        // and safe to skip when neither changed.
        if (retainIncompatible && inventoryChanged) {
            PerfTimer.start("incompatible.mark");
            Iterable<RecipeCollection> incTargets = incremental
                    ? (Iterable<RecipeCollection>) dirtySet : collections;
            for (RecipeCollection collection : incTargets) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
            PerfTimer.end("incompatible.mark");
        }

        // ── Partial material marking ──
        if (!retainPartial) {
            PerfTimer.logAndReset("updateCollections (incompatible-only)");
            return;
        }

        if (!inventoryChanged) {
            PerfTimer.logAndReset("updateCollections (cache-hit, fully skipped)");
            return;
        }

        // Activate generation tracking — wrapped in try/finally so that
        // filteringActive is always cleared even if an exception is thrown.
        PartialCraftingUtil.beginFilteringUpdate(true);
        try {
            java.util.Set<net.minecraft.world.item.Item> inventoryItems =
                    PartialCraftingUtil.hashInventory(this.menu.slots);

            // When incremental, only clear/mark dirty collections.
            // Non-dirty collections keep their previous partial marks
            // (which are still valid since their ingredients haven't changed).
            Iterable<RecipeCollection> targets = incremental
                    ? (Iterable<RecipeCollection>) dirtySet : collections;

            PerfTimer.start("partial.step0-clear");
            for (RecipeCollection collection : targets) {
                if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                    RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                    for (RecipeHolder<?> holder : collection.getRecipes()) {
                        if (PartialCraftingUtil.isPartiallyCraftable(collection, holder.id())) {
                            accessor.betterRecipeBook$getCraftable().remove(holder);
                        }
                    }
                }
            }
            PerfTimer.end("partial.step0-clear");

            PerfTimer.start("partial.markAndInject");
            int collCount = 0;
            for (RecipeCollection collection : targets) {
                collCount++;
                PartialCraftingUtil.markPartialMaterials(collection, inventoryItems);

                if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                    RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                    for (RecipeHolder<?> holder : collection.getRecipes()) {
                        if (PartialCraftingUtil.isPartiallyCraftable(collection, holder.id())) {
                            accessor.betterRecipeBook$getCraftable().add(holder);
                        }
                    }
                }
            }
            PerfTimer.end("partial.markAndInject");

            PerfTimer.logAndReset("updateCollections ("
                    + (incremental ? "incr:" : "") + collCount + " coll)");
        } finally {
            PartialCraftingUtil.beginFilteringUpdate(false);
        }
    }

    /**
     * Sorts collections so craftable recipes appear first, then partially-
     * craftable, then the rest.  1.21.1 calls page.updateCollections(List, boolean)
     * (2 params instead of 3 on 1.21.11+).
     */
    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void betterRecipeBook$sortBeforePageUpdate(RecipeBookPage page, List<RecipeCollection> list, boolean resetPageNumber) {
        PerfTimer.start("sort");
        boolean filtering = this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.player.getRecipeBook().isFiltering(this.menu);

        boolean shouldSort = BetterRecipeBook.config.partialCraftingEnabled
                || (BetterRecipeBook.config.partialMarkingEnabled && filtering);
        if (!shouldSort) {
            page.updateCollections(list, resetPageNumber);
            return;
        }

        boolean useFullSort = BetterRecipeBook.config.partialCraftingEnabled || filtering;
        boolean hasPartialData = BetterRecipeBook.config.partialMarkingEnabled;

        List<RecipeCollection> front = new ArrayList<>();
        List<RecipeCollection> middle = new ArrayList<>();
        List<RecipeCollection> back = new ArrayList<>();

        for (RecipeCollection c : list) {
            if (hasPartialData) {
                CollectionCategory cat = PartialCraftingUtil.categorize(c);
                if (useFullSort) {
                    switch (cat) {
                        case TRULY_CRAFTABLE -> front.add(c);
                        case PARTIAL -> middle.add(c);
                        case UNASSIGNED -> back.add(c);
                    }
                } else {
                    if (cat != CollectionCategory.UNASSIGNED) front.add(c);
                    else back.add(c);
                }
            } else {
                if (c.hasCraftable()) front.add(c); else back.add(c);
            }
        }

        list.clear();
        list.addAll(front);
        list.addAll(middle);
        list.addAll(back);
        PerfTimer.end("sort");

        // Capture the final sorted list for the TAIL cache save.
        this.brbe$lastPageList = list;

        page.updateCollections(list, resetPageNumber);
    }
}
