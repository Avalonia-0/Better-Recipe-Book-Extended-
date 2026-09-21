package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import com.alonie.brbe.util.WorkstationTitleTrigger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
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
 * open); clicks are routed to the overlay (button click places on crafting
 * screens, box background keeps it open, outside click closes it without falling
 * through to the container).  The overlay is drawn on the container's top render
 * stratum, above every widget, with its tooltip still flowing into the deferred
 * tooltip layer.</p>
 *
 * <p><b>ESC</b>：关闭已打开的查询窗口，但**不消费**这次按键——交回屏幕走原版
 * ESC 语义（"无论查询界面是否存在，用户都能用 ESC 退出界面"）。被 ESC 关掉的窗口
 * 不会在同一个界面里复活，界面一变就按持久化 spec 恢复（与 pin 一致）。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
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
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (PinOverlayManager.handleMouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
            return;
        }
        if (RecipeViewerOverlay.mouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
            return;
        }
        // 工作站标题触发：左键点击界面标题（合成/升级装备/厨锅…）→ 开新查询
        // 窗口查该工作站所属配方（窗口/pin 覆盖标题时上面的命中优先）。
        if (WorkstationTitleTrigger.clickTitle(event, screen)) {
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

    /** 工作站标题悬停的 tooltip/光标：挂在 <b>{@code extractContents}</b>
     *  RETURN——所有容器屏（含配方书屏）都会经 {@code super.extractContents}
     *  走到这里（AbstractRecipeBookScreen 不调 super.extractRenderState，挂
     *  在那里对合成/熔炉/锻造/厨锅等屏从不触发）。就地绘制原版 tooltip
     *  （style=null，非 BRBE 透明度调整款）；工作站不可解析时不显示。 */
    @Inject(method = "extractContents", at = @At("RETURN"))
    private void brbe$workstationTitleTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                              float delta, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (WorkstationTitleTrigger.titleHover(screen, mouseX, mouseY)) {
            gui.tooltip(Minecraft.getInstance().font,
                    java.util.List.of(ClientTooltipComponent.create(
                            WorkstationTitleTrigger.tooltip().getVisualOrderText())),
                    mouseX, mouseY,
                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                    null, false);
            gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
        }
    }

    /** GUI 打开瞬间（init）捕获"谁打开了这个界面"（准星方块/菜单映射），
     *  供工作站标题触发使用——此刻玩家右键的准星仍指向打开的方块，是最可信
     *  的原始事实；此后转视角不影响（触发按屏幕缓存）。 */
    @Inject(method = "init", at = @At("HEAD"))
    private void brbe$captureWorkstationStation(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        WorkstationTitleTrigger.capture(screen);
    }

    /** Close the viewer when its host screen closes.  Pins outlive the screen:
     *  they hide with it and reappear on the next container screen. */
    @Inject(method = "removed", at = @At("HEAD"))
    private void brbe$viewerOnScreenRemoved(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        RecipeViewerOverlay.onScreenClosed(screen);
    }

    /** Drag a pressed pin overlay (Screen has no mouseDragged; every container
     *  screen inherits this one from AbstractContainerScreen).  A title-bar
     *  drag of the query window wins first (window owns the cursor). */
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void brbe$pinMouseDragged(MouseButtonEvent event, double dx, double dy,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.mouseDragged(event)) {
            cir.setReturnValue(true);
            return;
        }
        if (PinOverlayManager.handleMouseDragged(event, dx, dy)) {
            cir.setReturnValue(true);
        }
    }

    /** Release ends a pin press: a release that never moved is a click that
     *  inherits the recipe button's click (placing the recipe); otherwise the
     *  drag ends.  A query-window title-bar drag ends first. */
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void brbe$pinMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (RecipeViewerOverlay.mouseReleased(event)) {
            cir.setReturnValue(true);
            return;
        }
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
     * Suppresses the container-slot item tooltip while the query UI owns the
     * cursor (viewer box / preview / pin cover the point).  Desktop-window
     * semantics: OUTSIDE the query UI the underlying container tooltips show
     * normally even while a window is open.
     */
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void brbe$suppressSlotTooltipWhileViewer(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                                     CallbackInfo ci) {
        if (RecipeViewerOverlay.modalMaskOwnsCursor(mouseX, mouseY)) {
            ci.cancel();
        }
    }
}
