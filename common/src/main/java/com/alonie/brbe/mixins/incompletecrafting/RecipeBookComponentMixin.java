package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
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
        // Step 1: Run original forEach (populates craftable + fitsDimensions)
        collections.forEach(consumer);

        // When "show all recipes in survival" is disabled, skip ALL partial/incompatible
        // logic. This prevents partial injection from corrupting the craftable set
        // and causing air placeholders.
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) {
            return;
        }

        boolean onInventory = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;

        // Mark incompatible recipes — gated by showAllRecipesInSurvival (outer guard),
        // NOT by partialMarkingEnabled, so "当前无法合成" recipes don't become air
        // placeholders when partialMarkingEnabled is off.
        if (onInventory) {
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }

        if (!BetterRecipeBook.config.partialMarkingEnabled) return;

        // Step 0: Clear previously-injected partial IDs from craftable set
        // so markPartialMaterials sees the vanilla state of isCraftable()
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

        for (RecipeCollection collection : collections) {
            // Step 2: Mark partial materials
            PartialCraftingUtil.markPartialMaterials(collection, this.menu.slots);

            // Step 3: Add partial recipes to craftable set so they are treated as craftable
            if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeHolder<?> holder : collection.getRecipes()) {
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, holder.id())) {
                        accessor.betterRecipeBook$getCraftable().add(holder);
                    }
                }
            }
        }
    }

    /**
     * Sorts collections so craftable recipes appear first, then partially-
     * craftable, then the rest.  1.21.1 calls page.updateCollections(List, boolean)
     * (2 params instead of 3 on 1.21.11+).
     */
    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void betterRecipeBook$sortBeforePageUpdate(RecipeBookPage page, List<RecipeCollection> list, boolean resetPageNumber) {
        if (!BetterRecipeBook.config.partialCraftingEnabled) {
            page.updateCollections(list, resetPageNumber);
            return;
        }
        List<RecipeCollection> craftable = new ArrayList<>();
        List<RecipeCollection> partial = new ArrayList<>();
        List<RecipeCollection> other = new ArrayList<>();

        for (RecipeCollection c : list) {
            boolean hasCraftable = c.hasCraftable();
            boolean hasPartial = PartialCraftingUtil.hasPartialMaterials(c);

            if (hasCraftable) {
                craftable.add(c);
            } else if (hasPartial) {
                partial.add(c);
            } else {
                other.add(c);
            }
        }

        list.clear();
        list.addAll(craftable);
        list.addAll(partial);
        list.addAll(other);
        page.updateCollections(list, resetPageNumber);
    }
}
