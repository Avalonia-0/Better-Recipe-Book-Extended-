package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.CollectionCategory;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.PerfTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
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

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void betterRecipeBook$injectIntoDataSets(
            List<RecipeCollection> collections, Consumer<? super RecipeCollection> consumer) {
        PerfTimer.begin();
        PerfTimer.start("vanilla.forEach");
        // Step 1: Run original forEach (populates craftable + fitsDimensions)
        collections.forEach(consumer);
        PerfTimer.end("vanilla.forEach");

        // ── Gate variables ──
        boolean onInventory = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        boolean retainPartial = BetterRecipeBook.config.partialMarkingEnabled;
        boolean retainIncompatible = onInventory
                && BetterRecipeBook.config.showAllRecipesInSurvival;

        // Only skip everything when BOTH features are off.
        if (!retainPartial && !retainIncompatible) {
            PerfTimer.logAndReset("updateCollections (no-op)");
            return;
        }

        // ── Incompatible recipe marking ──
        if (retainIncompatible) {
            PerfTimer.start("incompatible.mark");
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
            PerfTimer.end("incompatible.mark");
        }

        // ── Partial material marking ──
        if (!retainPartial) {
            PerfTimer.logAndReset("updateCollections (incompatible-only)");
            return;
        }

        // Activate generation tracking
        PartialCraftingUtil.beginFilteringUpdate(true);

        PerfTimer.start("hash.inventory");
        java.util.Set<net.minecraft.world.item.Item> inventoryItems = PartialCraftingUtil.hashInventory(this.menu.slots);
        long slotHash = PartialCraftingUtil.slotHash(this.menu.slots);
        PerfTimer.end("hash.inventory");

        // ── Slot-state cache: skip partial marking if inventory unchanged ──
        // updateCollections fires on every screen toggle, item pickup/drop, etc.
        // Most calls have identical inventory state.  The BRBE partial marking
        // pass (step0-clear + markAndInject) iterates all 25k collections, costing
        // ~30ms each call.  Skip it when nothing changed.
        long prevSlotHash = PartialCraftingUtil.getLastSlotHash();
        if (slotHash == prevSlotHash) {
            PartialCraftingUtil.beginFilteringUpdate(false);
            PerfTimer.logAndReset("updateCollections (cache-hit, skipped partial)");
            return;
        }
        PartialCraftingUtil.setLastSlotHash(slotHash);

        PerfTimer.start("partial.step0-clear");
        for (RecipeCollection collection : collections) {
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
        for (RecipeCollection collection : collections) {
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

        PartialCraftingUtil.beginFilteringUpdate(false);
        PerfTimer.logAndReset("updateCollections (" + collCount + " coll)");
    }

    /**
     * Sorts collections so craftable recipes appear first, then partially-
     * craftable, then the rest.  1.21.1 calls page.updateCollections(List, boolean)
     * (2 params instead of 3 on 1.21.11+).
     */
    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void betterRecipeBook$sortBeforePageUpdate(RecipeBookPage page, List<RecipeCollection> list, boolean resetPageNumber) {
        PerfTimer.start("sort");
        // 1.21.1 doesn't pass isFiltering to page.updateCollections —
        // read it from the player's recipe book instead.
        boolean filtering = this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.player.getRecipeBook().isFiltering(this.menu);

        // Sort when partialCraftingEnabled is active, or when
        // partialMarkingEnabled is active AND the vanilla filter is on.
        boolean shouldSort = BetterRecipeBook.config.partialCraftingEnabled
                || (BetterRecipeBook.config.partialMarkingEnabled && filtering);
        if (!shouldSort) {
            page.updateCollections(list, resetPageNumber);
            return;
        }

        // 3-group when partialCraftingEnabled is on (filter button hidden)
        // OR vanilla filter is active. Otherwise 2-group.
        boolean useFullSort = BetterRecipeBook.config.partialCraftingEnabled
                || filtering;
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
        page.updateCollections(list, resetPageNumber);
    }
}
