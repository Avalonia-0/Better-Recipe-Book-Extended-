package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected RecipeBookMenu<?, ?> menu;

    @Shadow @Final
    protected Minecraft minecraft;

    @Shadow
    private net.minecraft.client.gui.components.EditBox searchBox;

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z"))
    private boolean betterRecipeBook$keepPartiallyCraftable(List<RecipeCollection> collections, Predicate<? super RecipeCollection> predicate) {
        boolean onInventoryScreen = BetterRecipeBook.config.showAllRecipesInSurvival
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;

        for (RecipeCollection collection : collections) {
            PartialCraftingUtil.markPartialMaterials(collection, this.menu.slots);
            if (onInventoryScreen) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }

        boolean hasSearchActive = searchBox != null && !searchBox.getValue().isEmpty();
        boolean hasPartial = BetterRecipeBook.config.partialCraftableEqualsCraftable && !hasSearchActive;
        boolean retainIncompatible = onInventoryScreen && !hasSearchActive;

        if (!hasPartial && !retainIncompatible) {
            return collections.removeIf(predicate);
        }

        boolean removed = collections.removeIf(collection -> {
            if (!predicate.test(collection)) return false;
            if (hasPartial && PartialCraftingUtil.hasPartialMaterials(collection)) return false;
            if (retainIncompatible && IncompatibleCraftingUtil.hasIncompatibleRecipes(collection)) return false;
            return true;
        });

        if (hasPartial) {
            PartialCraftingUtil.sortCraftableBeforePartial(collections);
        }
        return removed;
    }
}
