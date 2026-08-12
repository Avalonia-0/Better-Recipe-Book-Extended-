package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Global host for the BRBE R/U recipe-viewer on every container screen.
 *
 * <p>R/U keys open the standalone viewer overlay (never forcing the recipe book
 * open); ESC dismisses only the overlay; clicks are routed to the overlay (button
 * click places on crafting screens, box background keeps it open, outside click
 * closes it without falling through to the container).  The overlay is drawn on
 * the container's top render stratum, above every widget, with its tooltip still
 * flowing into the deferred tooltip layer.</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (RecipeViewerOverlay.keyPressed(event, screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                         CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (RecipeViewerOverlay.mouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void brbe$viewerRender(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                   float delta, CallbackInfo ci) {
        RecipeViewerOverlay.render(gui, mouseX, mouseY, delta);
    }

    /** Close the viewer when its host screen is closed (setScreen away / E). */
    @Inject(method = "removed", at = @At("HEAD"))
    private void brbe$viewerOnScreenRemoved(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        RecipeViewerOverlay.onScreenClosed(screen);
    }

    /** Scroll over the paged viewer overlay flips its page. */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerMouseScrolled(double mouseX, double mouseY, double horizontal,
                                          double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.mouseScrolled(mouseX, mouseY, vertical)) {
            BetterRecipeBook.queuedScroll = 0;
            cir.setReturnValue(true);
        }
    }

    /**
     * Suppresses the container-slot item tooltip while a BRBE R/U viewer overlay is
     * open.  The viewer is a transient recipe browser; showing inventory item
     * tooltips underneath it is distracting, so they are hidden until the viewer
     * closes (ESC or an outside-click dismisses it, clearing viewerActive).
     */
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void brbe$suppressSlotTooltipWhileViewer(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                                     CallbackInfo ci) {
        if (RecipeViewerIndex.isViewerActive()) {
            ci.cancel();
        }
    }
}
