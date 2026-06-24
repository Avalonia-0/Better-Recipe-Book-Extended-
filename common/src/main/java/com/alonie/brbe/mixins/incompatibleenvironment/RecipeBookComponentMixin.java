package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow @Final private net.minecraft.client.ClientRecipeBook book;
    @Shadow private RecipeBookTabButton selectedTab;

    @Inject(method = "setupGhostRecipe", at = @At("HEAD"), cancellable = true)
    private void brbe$preventIncompatibleRecipeClick(
            RecipeHolder<?> recipe, List list, CallbackInfo ci) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<RecipeCollection> collections = this.book.getCollection(selectedTab.getCategory());
        if (collections == null) return;

        for (RecipeCollection collection : collections) {
            if (IncompatibleCraftingUtil.checkIncompatible(collection, recipe.id())) {
                ci.cancel();
                return;
            }
        }
    }
}
