package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.CollectionCategory;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
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
import java.util.function.Predicate;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected RecipeBookMenu menu;

    @Shadow @Final
    protected Minecraft minecraft;

    @Shadow
    private net.minecraft.client.gui.components.EditBox searchBox;

    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void betterRecipeBook$trackPartialFilteringUpdate(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        boolean retainIncompatible = BetterRecipeBook.config.showAllRecipesInSurvival
                && !isFiltering
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        IncompatibleCraftingUtil.beginFiltering(retainIncompatible);
    }

    // ordinal = 0: 26.1.2 has three removeIf(Predicate) calls inside
    // updateCollections.  Only intercept the first one (the main craftability
    // filter) so that the search filter and the crafting-table filter still
    // run vanilla's own predicate with our already-modified craftable set.
    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z", ordinal = 0))
    private boolean betterRecipeBook$keepPartiallyCraftable(List<RecipeCollection> collections, Predicate<? super RecipeCollection> predicate) {
        // ── Gate variables: single point of truth for each concern ──
        boolean onInventoryScreen = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        boolean retainPartial = BetterRecipeBook.config.partialMarkingEnabled;
        boolean retainIncompatible = onInventoryScreen
                && BetterRecipeBook.config.showAllRecipesInSurvival;
        // When showAllRecipesInSurvival is off, 3×3 recipes must never
        // be in PARTIAL_RECIPES — regardless of which screen we're on.
        // (The screen might not be InventoryScreen yet if updateCollections
        // fires during screen transition.)
        boolean filter3x3 = !BetterRecipeBook.config.showAllRecipesInSurvival;

        // Only skip everything when BOTH features are off.
        if (!retainPartial && !retainIncompatible) {
            return collections.removeIf(predicate);
        }

        // ── Partial material marking (gated inside PartialCraftingUtil) ──
        // Step 0: Clear previously-injected partial IDs from craftable set.
        // Skip 3×3 recipes when showAllRecipesInSurvival is off — they were
        // never injected (see injection guard below), so removing them would
        // only destroy vanilla's own craftable marking and cause
        // markPartialMaterials to see isCraftable()==false, re-tagging them
        // as partial.  That creates an infinite cycle where a fully-craftable
        // 3×3 recipe permanently shows the "partial" overlay.
        for (RecipeCollection collection : collections) {
            if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        if (filter3x3 && brbe$needsLargerGrid(entry.display())) {
                            continue; // never injected — leave vanilla craftable alone
                        }
                        accessor.betterRecipeBook$getCraftable().remove(id);
                    }
                }
            }
        }

        for (RecipeCollection collection : collections) {
            PartialCraftingUtil.markPartialMaterials(collection, this.menu.slots);

            // Inject partial recipes into craftable set
            if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        // Don't inject 3×3 partial recipes when showAllRecipesInSurvival
                        // is off — they can't be crafted in the 2×2 grid and would
                        // otherwise survive the vanilla filter, producing "air
                        // placeholder" ghost recipe slots.
                        if (filter3x3 && brbe$needsLargerGrid(entry.display())) {
                            continue;
                        }
                        accessor.betterRecipeBook$getCraftable().add(id);
                    }
                }
            }
        }

        // ── Root-cause cleanup: purge 3×3 recipes from the partial set ──
        // markPartialMaterials can be over-aggressive — it marks any recipe
        // that has at least one matching ingredient in the inventory and is
        // not already in the craftable set.  For 3×3 recipes when
        // showAllRecipesInSurvival=false, this is never useful: the
        // injection guard above skips them, and if we leave them in
        // PARTIAL_RECIPES Step 0 will destroy vanilla's craftable marking
        // on the next call, creating a permanent "partial" degradation loop.
        //
        // Removing them here (single source of truth) breaks the cycle
        // regardless of what guards exist in Step 0 / injection / keepPartial.
        if (filter3x3) {
            for (RecipeCollection collection : collections) {
                if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                    for (RecipeDisplayEntry entry : collection.getRecipes()) {
                        if (brbe$needsLargerGrid(entry.display())
                                && PartialCraftingUtil.isPartiallyCraftable(collection, entry.id())) {
                            PartialCraftingUtil.unmarkPartial(collection, entry.id());
                        }
                    }
                }
            }
        }

        // ── Incompatible recipe marking ──
        if (retainIncompatible) {
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }

        // ── Retention flags for the removeIf predicate ──
        boolean hasSearchActive = searchBox != null && !searchBox.getValue().isEmpty();
        boolean keepPartial = retainPartial && !hasSearchActive;
        boolean keepIncompatible = retainIncompatible
                && IncompatibleCraftingUtil.isActive()
                && !hasSearchActive;

        if (!keepPartial && !keepIncompatible) {
            return collections.removeIf(predicate);
        }

        boolean removed = collections.removeIf(collection -> {
            if (!predicate.test(collection)) return false;
            if (keepPartial && PartialCraftingUtil.hasPartialMaterials(collection)) {
                // Even if collection has partial materials, when
                // showAllRecipesInSurvival is off, skip the keep-partial
                // protection if EVERY partial recipe in the collection
                // needs a 3×3 grid. The injection loop above already skipped
                // those, so the collection would render as an air placeholder.
                if (filter3x3
                        && brbe$allPartialRecipesNeedLargerGrid(collection)) {
                    return true; // remove
                }
                return false;
            }
            if (keepIncompatible && IncompatibleCraftingUtil.hasIncompatibleRecipes(collection)) return false;
            return true;
        });

        return removed;
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"))
    private void betterRecipeBook$sortCraftableBeforePartial(
            RecipeBookPage page, List<RecipeCollection> list, boolean resetPageNumber, boolean isFiltering) {
        // Sort when partialCraftingEnabled is active, or when
        // partialMarkingEnabled is active AND the vanilla filter is on.
        boolean shouldSort = BetterRecipeBook.config.partialCraftingEnabled
                || (BetterRecipeBook.config.partialMarkingEnabled && isFiltering);
        if (!shouldSort) {
            page.updateCollections(list, resetPageNumber, isFiltering);
            return;
        }

        // 3-group when partialCraftingEnabled is on (filter button hidden)
        // OR vanilla filter is active. Otherwise 2-group.
        boolean useFullSort = BetterRecipeBook.config.partialCraftingEnabled
                || isFiltering;
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
        page.updateCollections(list, resetPageNumber, isFiltering);
    }

    /** True if a crafting display needs more than a 2×2 grid. */
    @Unique
    private static boolean brbe$needsLargerGrid(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.width() > 2 || shaped.height() > 2;
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients().size() > 4;
        }
        return false;
    }

    /** True if every partially-craftable recipe in the collection needs a 3×3 grid. */
    @Unique
    private static boolean brbe$allPartialRecipesNeedLargerGrid(RecipeCollection collection) {
        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (PartialCraftingUtil.isPartiallyCraftable(collection, entry.id())) {
                if (!brbe$needsLargerGrid(entry.display())) {
                    return false; // found at least one 2×2 partial recipe
                }
            }
        }
        return true; // all partial recipes are 3×3 (or there are no partial recipes)
    }
}
