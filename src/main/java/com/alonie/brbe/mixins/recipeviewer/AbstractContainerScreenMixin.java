package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
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
        if (PinOverlayManager.handleMouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
            return;
        }
        if (RecipeViewerOverlay.mouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
        }
        // Pins are passive windows: without the query viewer a click outside a
        // pin falls through to the container (item movement works normally).
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void brbe$viewerRender(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                   float delta, CallbackInfo ci) {
        // The creative inventory draws its own tab strip AFTER super, so the
        // viewer would sit under the tabs; CreativeModeInventoryScreenMixin
        // re-hosts the render at the creative method's RETURN instead.
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            return;
        }
        PinOverlayManager.render(gui, mouseX, mouseY, delta);
    }

    /** Close the viewer when its host screen closes.  Pins outlive the screen:
     *  they hide with it and reappear on the next container screen. */
    @Inject(method = "removed", at = @At("HEAD"))
    private void brbe$viewerOnScreenRemoved(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        RecipeViewerOverlay.onScreenClosed(screen);
    }

    /** Drag a pressed pin overlay (Screen has no mouseDragged; every container
     *  screen inherits this one from AbstractContainerScreen). */
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void brbe$pinMouseDragged(MouseButtonEvent event, double dx, double dy,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (PinOverlayManager.handleMouseDragged(event, dx, dy)) {
            cir.setReturnValue(true);
        }
    }

    /** Release ends a pin press: a release that never moved is a click that
     *  inherits the recipe button's click (placing the recipe); otherwise the
     *  drag ends. */
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void brbe$pinMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (PinOverlayManager.handleMouseReleased(event, screen)) {
            cir.setReturnValue(true);
        }
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
     * Suppresses the container-slot item tooltip while a BRBE R/U viewer overlay
     * is open.  Pins alone do not suppress it — they are passive windows and
     * the inventory tooltips underneath them show normally.
     */
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void brbe$suppressSlotTooltipWhileViewer(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                                     CallbackInfo ci) {
        if (RecipeViewerIndex.isViewerActive()) {
            ci.cancel();
        }
    }
}
