package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public abstract class IngredientListOverlayMixin {

    private static boolean warned;

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelIngredientListOverlay(CallbackInfo ci) {
        // Hidden while the config toggle is on OR while a BRBE overlay is open
        // (query viewer / pin): the ingredient list is drawn after BRBE's
        // layer and would cover BRBE's tooltips.  The reflection-based
        // OverlayHider path is unreliable against real JEI, so this mixin is
        // the authoritative gate.
        if (BetterRecipeBook.config != null
                && (BetterRecipeBook.config.hideReiJeiOverlay
                    || com.alonie.brbe.util.RecipeViewerOverlay.isActive()
                    || com.alonie.brbe.pinoverlay.PinOverlayManager.hasPins())) {
            if (!warned) {
                warned = true;
                BetterRecipeBook.LOGGER.warn(
                        "[BRBE] JEI ingredient overlay hidden (config or BRBE overlay open)");
            }
            ci.cancel();
        }
    }
}
