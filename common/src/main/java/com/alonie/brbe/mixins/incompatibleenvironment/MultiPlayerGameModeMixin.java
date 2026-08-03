package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents item placement for 3×3 recipes on the 2×2 inventory screen
 * when {@code showAllRecipesInSurvival} is ON.
 *
 * <p>In 1.21.1, recipe placement flows through
 * {@code MultiPlayerGameMode.handlePlaceRecipe}.  The
 * {@code setupGhostRecipe} cancellation in
 * {@code incompatibleenvironment/RecipeBookComponentMixin} only handles
 * the <em>preview</em> path (when the recipe is NOT craftable).  When
 * the pre-check elevates a 3×3 recipe to craftable, the preview path is
 * skipped and items would be placed directly — but the 2×2 grid can't
 * hold them.  This Mixin cancels the placement so items don't get stuck.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void brbe$preventLargeRecipePlacement(
            int containerId, RecipeHolder<?> recipe, boolean shiftKeyDown,
            CallbackInfo ci) {
        if (!BetterRecipeBook.ctx().config().showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof EffectRenderingInventoryScreen)) return;

        if (IncompatibleCraftingUtil.checkIncompatible(recipe)) {
            ci.cancel();
        }
    }
}
