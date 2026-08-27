package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.BookmarkOverlay", remap = false)
public abstract class BookmarkOverlayMixin {

    private static boolean warned;

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelBookmarkOverlay(CallbackInfo ci) {
        // Hidden while the config toggle is on OR while a BRBE overlay is open
        // (query viewer / pin) — see IngredientListOverlayMixin.
        if (BetterRecipeBook.config != null
                && (BetterRecipeBook.config.hideReiJeiOverlay
                    || com.alonie.brbe.util.RecipeViewerOverlay.isActive()
                    || com.alonie.brbe.pinoverlay.PinOverlayManager.hasPins())) {
            if (!warned) {
                warned = true;
                BetterRecipeBook.LOGGER.warn(
                        "[BRBE] JEI bookmark overlay hidden (config or BRBE overlay open)");
            }
            ci.cancel();
        }
    }
}
