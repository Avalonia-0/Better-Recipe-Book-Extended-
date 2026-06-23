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

    private static long brbe$lastSlotHash;

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void betterRecipeBook$injectIntoDataSets(
            List<RecipeCollection> collections, Consumer<? super RecipeCollection> consumer) {
        PerfTimer.begin();

        // ── Compute slot hash BEFORE calling vanilla forEach ──
        long slotHash = PartialCraftingUtil.slotHash(this.menu.slots);
        boolean inventoryChanged = (slotHash != brbe$lastSlotHash);

        if (inventoryChanged) {
            PerfTimer.start("vanilla.forEach");
            collections.forEach(consumer);
            PerfTimer.end("vanilla.forEach");
            brbe$lastSlotHash = slotHash;
        } else {
            // Vanilla forEach re-populates craftable from scratch (150ms on ATM10).
            // RecipeCollection objects are reused across updateCollections calls;
            // their craftable/fitsDimensions data from the previous call is still
            // valid when inventory hasn't changed.  Skip the entire forEach.
        }

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

        // Skip BRBE partial pass when inventory hasn't changed AND the
        // vanilla forEach was also skipped (craftable data is still fresh).
        if (!inventoryChanged) {
            PerfTimer.logAndReset("updateCollections (cache-hit, fully skipped)");
            return;
        }

        // Activate generation tracking — wrapped in try/finally so that
        // filteringActive is always cleared even if an exception is thrown
        // (prevents permanent cache-bypass from a leaked filteringActive=true).
        PartialCraftingUtil.beginFilteringUpdate(true);
        try {
            java.util.Set<net.minecraft.world.item.Item> inventoryItems = PartialCraftingUtil.hashInventory(this.menu.slots);

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

            PerfTimer.logAndReset("updateCollections (" + collCount + " coll)");
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
