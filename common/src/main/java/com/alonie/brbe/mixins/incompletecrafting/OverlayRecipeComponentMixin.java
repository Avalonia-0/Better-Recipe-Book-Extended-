package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OverlayRecipeComponent.class)
public class OverlayRecipeComponentMixin {

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;isCraftable(Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
    private boolean betterRecipeBook$markPartialAsCraftableInOverlay(RecipeCollection collection, RecipeHolder<?> recipe) {
        if (!BetterRecipeBook.config.partialCraftableEqualsCraftable) {
            return collection.isCraftable(recipe);
        }
        return collection.isCraftable(recipe) || PartialCraftingUtil.isPartiallyCraftable(collection, recipe);
    }
}
