package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that cancels {@code IconButton.draw()} for JEI's bottom overlay toolbar
 * buttons when BRBE's "Hide REI/JEI" config is enabled.
 * <p>
 * Only buttons positioned within the bottom {@value #BOTTOM_THRESHOLD} GUI-scaled
 * pixels of the screen are cancelled — this matches the JEI overlay toolbar
 * (config, bookmark, history buttons) while leaving recipe-viewer navigation
 * buttons (positioned mid-screen) unaffected.
 * <p>
 * Uses {@code targets} (string) instead of {@code value} (class literal) so that
 * the Mixin annotation processor does not require the JEI jar at compile time.
 * {@code remap = false} ensures no refmap remapping is attempted.
 * <p>
 * Button Y-position is read via reflection to avoid a compile-time dependency
 * on {@code mezz.jei.gui.elements.IconButton}.
 */
@Mixin(targets = "mezz.jei.gui.elements.IconButton", remap = false)
public abstract class IconButtonMixin {

    /** GUI-scaled pixels from the bottom of the window. JEI's bottom toolbar
     * buttons are at ~22 px from the bottom (BORDER_MARGIN=6 + BUTTON_SIZE=16). */
    private static final int BOTTOM_THRESHOLD = 40;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelBottomJeiIconButtonDraw(CallbackInfo ci) {
        if (BetterRecipeBook.config == null || !BetterRecipeBook.config.hideReiJeiOverlay) {
            return;
        }
        try {
            int y = (int) this.getClass().getMethod("getY").invoke(this);
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            if (y > screenHeight - BOTTOM_THRESHOLD) {
                ci.cancel();
            }
        } catch (Exception ignored) {
            // Cannot determine position — do not cancel (safe default)
        }
    }
}
