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
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ported from 1.21.1: uses forEach consumer redirect + data layer injection
 * instead of removeIf/predicate patches.
 *
 * After updateCollections() runs its internal forEach consumer (which populates
 * craftable + fitsDimensions sets), we inject additional recipes into the data
 * sets so getRecipes(boolean) naturally includes them:
 *
 * 1. Partial recipes → craftable set (when partialCraftableEqualsCraftable)
 * 2. Incompatible (3x3) recipes → fitsDimensions set (via markIncompatibleRecipes)
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected RecipeBookMenu menu;

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
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        accessor.betterRecipeBook$getCraftable().add(id);
                    }
                }
            }

            // Step 4: If enabled, mark incompatible recipes
            //         (markIncompatibleRecipes also writes to fitsDimensions)
            if (onInventory) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }
    }

    /**
     * After all removeIf calls have run on the mutable list copy, sort collections
     * so fully-craftable collections appear before partial-material ones.
     */
    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"))
    private void betterRecipeBook$sortCraftableBeforePartial(
            RecipeBookPage page, List<RecipeCollection> list, boolean resetPageNumber, boolean isFiltering) {
        if (BetterRecipeBook.config.partialCraftableEqualsCraftable) {
            List<RecipeCollection> craftable = new ArrayList<>();
            List<RecipeCollection> partial = new ArrayList<>();
            List<RecipeCollection> other = new ArrayList<>();
            for (RecipeCollection c : list) {
                boolean hasCraftable = c.hasCraftable();
                boolean hasPartial = PartialCraftingUtil.hasPartialMaterials(c);
                if (hasCraftable && !hasPartial) {
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
        }
        page.updateCollections(list, resetPageNumber, isFiltering);
    }
}
