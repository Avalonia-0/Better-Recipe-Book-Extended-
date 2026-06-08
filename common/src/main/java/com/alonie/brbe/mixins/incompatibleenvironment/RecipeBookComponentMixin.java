package com.alonie.brbe.mixins.incompatibleenvironment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents ghost recipe preview and recipe click handling for 3×3
 * recipes displayed in the 2×2 inventory crafting grid.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "recipesClicked", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$preventIncompatibleRecipeClick(
            RecipeHolder<?> recipe, boolean isFiltering, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        if (recipe.value() instanceof ShapedRecipe shaped
                && (shaped.getWidth() > 2 || shaped.getHeight() > 2)) {
            // Prevent recipe placement in the 2×2 grid
            ci.cancel();
        }
    }
}
