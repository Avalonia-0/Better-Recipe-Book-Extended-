package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 26.1.2 replacement for {@code updateCollections} which no longer exists.
 * Uses {@code tick()} to mark partially-craftable recipes and retain them
 * during filtering, and marks them as craftable so the recipe book filter
 * doesn't remove them.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow
    @Final
    protected RecipeBookMenu menu;

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    private ClientRecipeBook book;

    @Shadow
    public abstract boolean isVisible();

    @Unique
    private boolean betterRecipeBook$filteringActive;

    @Inject(method = "tick", at = @At("TAIL"))
    private void betterRecipeBook$onTick(CallbackInfo ci) {
        if (!BetterRecipeBook.config.partialCraftableEqualsCraftable
                && !BetterRecipeBook.config.showAllRecipesInSurvival) {
            return;
        }

        boolean onInventoryScreen = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;

        if (!onInventoryScreen && !this.isVisible()) {
            return;
        }

        // Track filtering toggle state (26.1.2: isFiltering takes RecipeBookType)
        boolean isFiltering = this.book.isFiltering(this.menu.getRecipeBookType());
        if (isFiltering != betterRecipeBook$filteringActive) {
            betterRecipeBook$filteringActive = isFiltering;
            PartialCraftingUtil.beginFilteringUpdate(
                    BetterRecipeBook.config.partialCraftableEqualsCraftable && isFiltering);
            IncompatibleCraftingUtil.beginFiltering(
                    BetterRecipeBook.config.showAllRecipesInSurvival
                            && onInventoryScreen && !isFiltering);
        }

        // Mark partial materials on all collections
        List<RecipeCollection> allCollections = this.book.getCollections();
        if (allCollections == null) return;

        for (RecipeCollection collection : allCollections) {
            if (BetterRecipeBook.config.partialCraftableEqualsCraftable) {
                PartialCraftingUtil.markPartialMaterials(collection, this.menu.slots);
                // Add to craftable set so the recipe book's filter doesn't remove them
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, entry.id())) {
                        accessor.betterRecipeBook$getCraftable().add(entry.id());
                    }
                }
            }
            if (BetterRecipeBook.config.showAllRecipesInSurvival && onInventoryScreen) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }
    }
}
