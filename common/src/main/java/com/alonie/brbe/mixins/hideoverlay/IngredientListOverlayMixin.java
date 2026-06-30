package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels JEI's IngredientListOverlay.drawScreen() when hideReiJeiOverlay is enabled.
 */
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public abstract class IngredientListOverlayMixin {

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelIngredientListOverlay(CallbackInfo ci) {
        if (BetterRecipeBook.ctx().config() != null && BetterRecipeBook.ctx().config().hideReiJeiOverlay) {
            ci.cancel();
        }
    }
}
