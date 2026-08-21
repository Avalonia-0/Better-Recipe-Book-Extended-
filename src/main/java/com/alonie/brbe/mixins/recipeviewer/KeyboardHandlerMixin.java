package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the BRBE R/U viewer priority over mods that hook the keyboard layer.
 * JEI intercepts R/U in Fabric's {@code ScreenKeyboardEvents.allowKeyPress}
 * pre-event, which fires from {@code KeyboardHandler} <em>before</em>
 * {@code Screen.keyPressed} is ever called — so the viewer's own keyPressed
 * injections on the container screens would never run.  Handling the keys here,
 * at the {@code KeyboardHandler} entry (highest priority), lets BRBE win while
 * falling through to JEI/vanilla whenever the viewer cannot show anything.
 *
 * <p>{@code keyPress} receives both the press and the release of a key (the
 * {@code KeyEvent} carries no action flag), so only a key currently held down
 * is handled, and a key already handled during this hold is skipped — this
 * avoids re-running the query on release and on OS key-repeat.</p>
 */
@Mixin(value = KeyboardHandler.class, priority = 2000)
public abstract class KeyboardHandlerMixin {

    /** Key code of the R/U key already consumed during the current key hold. */
    @Unique
    private static int brbe$activeKeyCode = -1;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerKeysEarly(long window, int key, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        Screen screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            brbe$activeKeyCode = -1;
            return;
        }
        int keyCode = event.key();
        if (!InputConstants.isKeyDown(mc.getWindow(), keyCode)) {
            // Release of a consumed key ends the hold.
            if (keyCode == brbe$activeKeyCode) {
                brbe$activeKeyCode = -1;
            }
            return;
        }
        if (keyCode == brbe$activeKeyCode) {
            // OS key-repeat of a key we already consumed this hold.
            return;
        }
        if (RecipeViewerOverlay.keyPressed(event, containerScreen)
                || PinOverlayManager.handleKeyPressed(event, containerScreen)) {
            brbe$activeKeyCode = keyCode;
            ci.cancel();
        }
    }
}
