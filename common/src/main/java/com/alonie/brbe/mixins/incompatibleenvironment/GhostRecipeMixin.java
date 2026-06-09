package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.GhostRecipe", remap = false)
public abstract class GhostRecipeMixin {

    @Inject(method = "addRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void betterRecipeBook$preventIncompatibleGhost(
            RecipeDisplayEntry entry, RecipeCollection collection,
            Object registryAccess, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;
        if (collection == null) return;

        if (IncompatibleCraftingUtil.isIncompatible(collection, entry.id())) {
            ci.cancel();
        }
    }
}
