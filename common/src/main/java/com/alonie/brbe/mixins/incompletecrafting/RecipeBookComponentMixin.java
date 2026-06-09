package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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

        boolean onInventory = BetterRecipeBook.config.showAllRecipesInSurvival
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;

        for (RecipeCollection collection : collections) {
            // Step 2: Mark partial materials
            PartialCraftingUtil.markPartialMaterials(collection, this.menu.slots);

            // Step 3: If enabled, add partial recipes to craftable set so the
            //         craftable filter doesn't remove them
            if (BetterRecipeBook.config.partialCraftableEqualsCraftable
                    && PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeHolder<?> holder : collection.getRecipes()) {
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, holder.id())) {
                        accessor.betterRecipeBook$getCraftable().add(holder);
                    }
                }
            }

            // Step 4: If enabled, add incompatible recipes to fitsDimensions so
            //         getRecipes(false) and search naturally include them
            if (onInventory) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }
    }
}
