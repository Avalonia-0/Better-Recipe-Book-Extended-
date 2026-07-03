package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts A key on container screens when the "Hide REI/JEI Overlay" config
 * is enabled, consuming it to prevent REI/JEI favorites from processing.
 * Skips interception when a text field (search box) is focused.
 *
 * <p>R and U keys are intentionally not handled — recipe/usage lookup is
 * delegated entirely to the recipe viewer mod (JEI/REI).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    private static final int KEY_A = 65;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$handleKeysOnHiddenOverlay(int keyCode, int scancode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!BetterRecipeBook.ctx().config().hideReiJeiOverlay) {
            return;
        }

        if (keyCode == KEY_A) {
            Screen screen = (Screen) (Object) this;
            if (!(screen.getFocused() instanceof EditBox)) {
                cir.setReturnValue(true);
            }
        }
    }
}
