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
        // Hidden only while the config toggle is on.  BRBE overlays (query
        // viewer / pins) must NOT hide the real JEI: co-existence is expected
        // and the ingredient list stays interactive beside the viewer.  The
        // reflection-based OverlayHider path is unreliable against real JEI,
        // so this mixin is the authoritative gate for the config toggle.
        if (BetterRecipeBook.config != null
                && BetterRecipeBook.config.hideReiJeiOverlay) {
            if (!warned) {
                warned = true;
                BetterRecipeBook.LOGGER.warn(
                        "[BRBE] JEI ingredient overlay hidden (config toggle)");
            }
            ci.cancel();
        }
    }
}
