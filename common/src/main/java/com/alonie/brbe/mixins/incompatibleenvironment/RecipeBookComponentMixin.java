package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents recipe placement for 3×3 recipes in the 2×2 inventory grid.
 * Mixes into the parent class so the inherited method is caught regardless
 * of which subclass is active.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "recipesClicked", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$preventIncompatibleRecipePlacement(
            RecipeDisplayEntry entry, RecipeCollection collection, boolean filtering,
            CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;
        if (IncompatibleCraftingUtil.isIncompatible(collection, entry.id())) {
            ci.cancel();
        }
    }
}
