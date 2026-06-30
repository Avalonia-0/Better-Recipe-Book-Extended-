package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels JEI's BookmarkOverlay.drawScreen() when hideReiJeiOverlay is enabled.
 */
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.BookmarkOverlay", remap = false)
public abstract class BookmarkOverlayMixin {

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelBookmarkOverlay(CallbackInfo ci) {
        if (BetterRecipeBook.ctx().config() != null && BetterRecipeBook.ctx().config().hideReiJeiOverlay) {
            ci.cancel();
        }
    }
}
