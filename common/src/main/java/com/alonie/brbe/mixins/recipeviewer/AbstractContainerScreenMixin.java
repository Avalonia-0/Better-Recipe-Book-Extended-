package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.1 版容器屏 viewer/pin 宿主 mixin（1.21.11 AbstractContainerScreenMixin 移植）。
 *
 * <p>1.21.1 签名差异：keyPressed(int,int,int)、mouseClicked(double,double,int)、
 * mouseDragged(double,double,int,double,double)、mouseReleased(double,double,int)、
 * mouseScrolled(double,double,double,double)、renderTooltip(GuiGraphics,int,int)。</p>
 *
 * <p>键位/点击的事件处理与 1.21.11 完全相同；渲染在 1.21.1 走平台 after-render
 * 钩子（TopLayerOverlayRenderer.renderViewer，见入口）——那里调用
 * {@code PinOverlayManager.render}（其内部再调 RecipeViewerOverlay.render，
 * z 序交错 pin 与 viewer），故本 mixin 不重复挂 render RETURN。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerKeys(int keyCode, int scancode, int modifiers,
                                 CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (RecipeViewerOverlay.keyPressed(keyCode, scancode, modifiers, screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerMouseClicked(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (PinOverlayManager.handleMouseClicked(mouseX, mouseY, button, screen)) {
            cir.setReturnValue(true);
            return;
        }
        if (RecipeViewerOverlay.mouseClicked(mouseX, mouseY, button, screen)) {
            cir.setReturnValue(true);
        }
    }

    /** Close the viewer when its host screen closes.  Pins outlive the screen:
     *  they hide with it and reappear on the next container screen. */
    @Inject(method = "removed", at = @At("HEAD"))
    private void brbe$viewerOnScreenRemoved(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        RecipeViewerOverlay.onScreenClosed(screen);
    }

    /** Drag a pressed pin overlay. */
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void brbe$pinMouseDragged(double mouseX, double mouseY, int button,
                                      double dragX, double dragY,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (PinOverlayManager.handleMouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    /** Release ends a pin press: a release that never moved is a click that
     *  inherits the recipe button's click (placing the recipe); otherwise the
     *  drag ends. */
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void brbe$pinMouseReleased(double mouseX, double mouseY, int button,
                                       CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (PinOverlayManager.handleMouseReleased(mouseX, mouseY, button, screen)) {
            cir.setReturnValue(true);
        }
    }

    /** Suppress the container-slot item tooltip while the viewer is open
     *  (pins alone do not suppress it). */
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void brbe$suppressSlotTooltipWhileViewer(GuiGraphics gui, int mouseX, int mouseY,
                                                     CallbackInfo ci) {
        if (com.alonie.brbe.cache.RecipeViewerIndex.isViewerActive()) {
            ci.cancel();
        }
    }
}
