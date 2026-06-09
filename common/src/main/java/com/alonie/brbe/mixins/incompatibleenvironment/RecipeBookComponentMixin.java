package com.alonie.brbe.mixins.incompatibleenvironment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "recipesClicked", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$blockIncompatibleRecipePlacement(
            RecipeDisplayEntry entry, boolean isFiltering, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen
                && entry.display() instanceof ShapedCraftingRecipeDisplay shaped
                && (shaped.width() > 2 || shaped.height() > 2)) {
            ci.cancel();
        }
    }
}
