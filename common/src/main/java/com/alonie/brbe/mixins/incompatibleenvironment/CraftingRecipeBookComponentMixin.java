package com.alonie.brbe.mixins.incompatibleenvironment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingRecipeBookComponent.class)
public abstract class CraftingRecipeBookComponentMixin {

    @Inject(method = "canDisplay", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$showAllRecipes(RecipeDisplay recipeDisplay, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
                && Minecraft.getInstance().screen instanceof InventoryScreen
                && (recipeDisplay instanceof ShapedCraftingRecipeDisplay
                    || recipeDisplay instanceof ShapelessCraftingRecipeDisplay)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "fillGhostRecipe", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$skipGhostRecipeForIncompatible(
            GhostSlots ghostSlots, RecipeDisplay display, ContextMap contextMap,
            CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;
        if (isIncompatibleShape(display)) {
            ci.cancel();
        }
    }

    /** Block recipe placement for incompatible recipes in the 2x2 grid. */
    @Inject(method = "recipesClicked", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$blockIncompatibleRecipePlacement(
            RecipeDisplayEntry entry, boolean isFiltering, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen
                && isIncompatibleShape(entry.display())) {
            ci.cancel();
        }
    }

    private static boolean isIncompatibleShape(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.width() > 2 || shaped.height() > 2;
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients().size() > 4;
        }
        return false;
    }
}
