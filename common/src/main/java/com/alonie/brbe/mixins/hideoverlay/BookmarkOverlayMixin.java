package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels JEI's {@code BookmarkOverlay.drawScreen()} when the
 * "Hide REI/JEI" config is enabled. This prevents the bookmark list,
 * bookmark button, and history button from rendering on container screens.
 * <p>
 * Uses {@code targets} (string) instead of {@code value} (class literal) and
 * {@code remap = false} so that the Mixin annotation processor requires no
 * JEI classes at compile time, working correctly under {@code loom-no-remap}.
 * <p>
 * This is the 26.1.2 equivalent of 1.21.11's Fabric mixin that targets
 * {@code BookmarkOverlay.drawScreen()} directly.
 */
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.BookmarkOverlay", remap = false)
public abstract class BookmarkOverlayMixin {

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelBookmarkOverlay(CallbackInfo ci) {
        if (BetterRecipeBook.config != null && BetterRecipeBook.config.hideReiJeiOverlay) {
            ci.cancel();
        }
    }
}
