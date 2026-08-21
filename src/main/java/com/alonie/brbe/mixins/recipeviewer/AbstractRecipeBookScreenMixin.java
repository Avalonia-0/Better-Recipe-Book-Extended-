package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Recipe-book screens override {@code keyPressed} / {@code mouseClicked} /
 * {@code extractRenderState} without delegating every path to their
 * {@code AbstractContainerScreen} superclass, so the viewer needs the same three
 * hooks here as on {@code AbstractContainerScreenMixin} — this mixin runs first
 * (the subclass method is entered before {@code super.…}), so R/U and ESC win
 * over the recipe-book component and the standalone overlay renders on top of
 * the book.  All logic lives in {@link RecipeViewerOverlay}.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractRecipeBookScreen<?> screen = (AbstractRecipeBookScreen<?>) (Object) this;
        if (RecipeViewerOverlay.keyPressed(event, screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                         CallbackInfoReturnable<Boolean> cir) {
        AbstractRecipeBookScreen<?> screen = (AbstractRecipeBookScreen<?>) (Object) this;
        if (PinOverlayManager.handleMouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
            return;
        }
        if (RecipeViewerOverlay.mouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void brbe$viewerRender(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                   float delta, CallbackInfo ci) {
        PinOverlayManager.render(gui, mouseX, mouseY, delta);
    }
}
