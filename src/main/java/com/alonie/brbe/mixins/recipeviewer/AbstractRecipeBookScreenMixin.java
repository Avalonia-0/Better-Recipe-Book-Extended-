package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
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
 * {@code render} without delegating every path to their
 * {@code AbstractContainerScreen} superclass, so the viewer needs the same three
 * hooks here as on {@code AbstractContainerScreenMixin} — this mixin's HEAD
 * injectors run before the vanilla body (i.e. before the recipe-book
 * component), so R/U and ESC win over the book and the standalone overlay
 * renders on top of it.  All logic lives in {@link RecipeViewerOverlay}.
 *
 * <p>2026-09-13（替代配方组开着时 LEI 完全无法操作）：「在方法体之前」并不等于
 * 「在所有 HEAD 注入器之前」——同一注入点上的多个注入器按 mixin 注册顺序执行，而
 * {@code pins.AbstractContainerScreenMixin} 注册在本 mixin 之前，原本会在替代配方组
 * 可见时（原版 {@code RecipeBookPage.mouseClicked} 对任意点击都返回 true）把点击整个
 * 吞掉，LEI 的处理器永远轮不到。现在那一侧加了「LEI 浮层上的点击一律让出」的守卫，
 * 注入器顺序不再影响结果：LEI 先挑，其余点击照旧归配方书。</p>
 *
 * <p>Modal-window guards: the screen overrides keyPressed / mouseDragged /
 * charTyped without delegating every path, so each gets a viewer-first or
 * swallow-while-active guard here.</p>
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractRecipeBookScreen<?> screen = (AbstractRecipeBookScreen<?>) (Object) this;
        if (RecipeViewerOverlay.keyPressed(event, screen)) {
            cir.setReturnValue(true);
            return;
        }
        // Not a query-window key: the desktop below stays interactive (window
        // semantics, not a full-screen focus layer).
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

    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$viewerRender(GuiGraphics gui, int mouseX, int mouseY,
                                   float delta, CallbackInfo ci) {
        PinOverlayManager.render(gui, mouseX, mouseY, delta);
    }

    /** Recipe-book screens override mouseDragged (routing into the book
     *  component BEFORE the container chain) — a title-bar drag of the query
     *  window must win there too, and pin dragging still works. */
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerDrag(MouseButtonEvent event, double dx, double dy,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.mouseDragged(event)) {
            cir.setReturnValue(true);
            return;
        }
        if (PinOverlayManager.handleMouseDragged(event, dx, dy)) {
            cir.setReturnValue(true);
        }
    }
}
