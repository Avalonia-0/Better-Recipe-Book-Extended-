package com.alonie.brbe.mixins.incompatibleenvironment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the grid-size filter from the 2×2 inventory crafting grid so that
 * 3×3 recipes also appear in the recipe book.  The vanilla
 * {@code canDisplay()} checks {@code shaped.width() <= gridWidth} etc.;
 * on {@link InventoryScreen} we override it to always return {@code true}.
 * <p>
 * Incompatibility is communicated via a red tooltip, not by hiding recipes.
 */
@Mixin(CraftingRecipeBookComponent.class)
public abstract class CraftingRecipeBookComponentMixin {

    @Inject(method = "canDisplay", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$showAllRecipes(RecipeDisplay recipeDisplay, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && Minecraft.getInstance().screen instanceof InventoryScreen) {
            cir.setReturnValue(true);
        }
    }
}
