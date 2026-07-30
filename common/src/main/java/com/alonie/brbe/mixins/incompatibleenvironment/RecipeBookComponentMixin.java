package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
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

    /**
     * Prevent ghost-recipe preview for 3×3 recipes on the 2×2 inventory
     * screen.  The preview shows items that would be placed, but since
     * the grid is too small the placement would fail silently — leaving
     * items stuck in the crafting grid.
     *
     * <p>In 1.21.1, {@code setupGhostRecipe} is the only client-side
     * entry point for recipe placement.  When cancelled, neither the
     * preview nor the actual item transfer occurs.  (There is no
     * {@code handleRecipeClicked} method on {@code RecipeBookComponent}
     * in this version — placement is driven entirely through the ghost
     * recipe + server packet round-trip.)
     */
    @Inject(method = "setupGhostRecipe", at = @At("HEAD"), cancellable = true)
    private void brbe$preventIncompatibleRecipeClick(
            RecipeHolder<?> recipe, List list, CallbackInfo ci) {
        if (!BetterRecipeBook.ctx().config().showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof EffectRenderingInventoryScreen)) return;

        if (IncompatibleCraftingUtil.checkIncompatible(recipe)) {
            ci.cancel();
            return;
        }

        // Fallback: also check via vanilla collections (handles edge cases)
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
