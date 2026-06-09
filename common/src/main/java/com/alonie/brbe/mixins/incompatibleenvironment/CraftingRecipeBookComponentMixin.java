package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingRecipeBookComponent.class)
public abstract class CraftingRecipeBookComponentMixin {

    @Inject(method = "canDisplay", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$showAllRecipes(RecipeDisplay recipeDisplay, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
                && BetterRecipeBook.config.showAllRecipesInSurvival
                && Minecraft.getInstance().screen instanceof InventoryScreen) {
            cir.setReturnValue(true);
        }
    }
}
