package com.alonie.brbe.mixins.unlockrecipes;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "handlePlaceRecipe", at = @At(value = "HEAD"), cancellable = true)
    public void onPlaceRecipe(int z, RecipeDisplayId recipe, boolean shiftKeyDown, CallbackInfo ci) {
        if (BetterRecipeBook.config.newRecipes.unlockAll) {
            // Let the server handle recipe placement for unlocked recipes
        }
    }
}
