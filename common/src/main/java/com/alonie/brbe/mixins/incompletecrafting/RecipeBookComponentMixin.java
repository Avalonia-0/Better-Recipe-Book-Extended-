package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected RecipeBookMenu<?, ?> menu;

    @Shadow @Final
    protected Minecraft minecraft;

    @Shadow
    private ClientRecipeBook book;

    @Unique
    private boolean betterRecipeBook$isCraftableFiltering;

    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void betterRecipeBook$resetFilteringState(boolean resetPageNumber, CallbackInfo ci) {
        betterRecipeBook$isCraftableFiltering = false;
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ClientRecipeBook;isFiltering(Lnet/minecraft/world/inventory/RecipeBookMenu;)Z"))
    private boolean betterRecipeBook$trackFiltering(ClientRecipeBook instance, RecipeBookMenu<?, ?> menu) {
        boolean filtering = instance.isFiltering(menu);
        betterRecipeBook$isCraftableFiltering = filtering && BetterRecipeBook.config.partialCraftableEqualsCraftable;
        PartialCraftingUtil.beginFilteringUpdate(filtering);

        // Incompatible (3x3) recipes are marked regardless of filtering state,
        // but retained at the collection level only when NOT filtering.
        boolean onInventory = BetterRecipeBook.config.showAllRecipesInSurvival
                && !filtering
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        IncompatibleCraftingUtil.beginFiltering(onInventory);

        return filtering;
    }

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

        if (!betterRecipeBook$isCraftableFiltering) {
            return collections.removeIf(predicate);
        }

        boolean removed = collections.removeIf(collection ->
                predicate.test(collection) && !PartialCraftingUtil.hasPartialMaterials(collection));

        PartialCraftingUtil.sortCraftableBeforePartial(collections);
        return removed;
    }
}
