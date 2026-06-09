package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "recipesClicked", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$preventIncompatibleRecipeClick(
            RecipeHolder<?> recipe, boolean isFiltering, CallbackInfo ci) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        RecipeCollection collection = this.getRecipeCollection(recipe);
        if (collection == null) return;

        if (IncompatibleCraftingUtil.checkIncompatible(collection, recipe.id())) {
            ci.cancel();
        }
    }

    public abstract RecipeCollection getRecipeCollection(RecipeHolder<?> recipe);
}
