package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that cancels {@code IconButton.draw()} when BRBE's "Hide REI/JEI" config is enabled.
 * <p>
 * Uses {@code targets} (string) instead of {@code value} (class literal) so that the Mixin
 * annotation processor does not require the JEI jar at compile time for the common module.
 * {@code remap = false} ensures no refmap remapping is attempted — IconButton is a JEI class,
 * not a Minecraft class, so its method names never change.
 * <p>
 * This approach works on both {@code loom} and {@code loom-no-remap} because the injected
 * handler references only {@link CallbackInfo} — no Minecraft parameter types that would
 * differ between Yarn/intermediary and Mojang mappings.
 */
@Mixin(targets = "mezz.jei.gui.elements.IconButton", remap = false)
public abstract class IconButtonMixin {

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelJeiIconButtonDraw(CallbackInfo ci) {
        if (BetterRecipeBook.config != null && BetterRecipeBook.config.hideReiJeiOverlay) {
            ci.cancel();
        }
    }
}
