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
 * Prevents ghost recipe preview for recipes marked as incompatible
 * (3×3 in a 2×2 grid) by checking {@link IncompatibleCraftingUtil}.
 * This takes priority over vanilla's craftability check.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "recipesClicked", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$preventIncompatibleGhostRecipe(
            RecipeDisplayEntry entry, boolean isFiltering, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        RecipeCollection collection = this.getRecipeCollection(entry);
        if (collection == null) return;

        if (IncompatibleCraftingUtil.isIncompatible(collection, entry.id())) {
            ci.cancel();
        }
    }

    // Shadow for getRecipeCollection — declared differently in subclasses
    // but this is just the abstract declaration, resolved at runtime.
    @SuppressWarnings("unused")
    public abstract RecipeCollection getRecipeCollection(RecipeDisplayEntry entry);
}
