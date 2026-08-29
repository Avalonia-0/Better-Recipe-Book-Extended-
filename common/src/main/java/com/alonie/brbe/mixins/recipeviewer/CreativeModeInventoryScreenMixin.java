package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.1 版创造屏重宿主（1.21.11 CreativeModeInventoryScreenMixin 移植）。
 *
 * <p>创造屏的 tab 条在 {@code super.render} 之后绘制、其自身 mouseClicked 覆盖
 * 在 super 之前处理 tab 点击——BRBE viewer/pin（挂在渲染尾部 after-render 的
 * 平台钩子上）在点击上会被创造标签先消费。本 mixin 把点击路由到 viewer/pin
 * 优先，并抑制 viewer 打开时创造标签的悬停态（checkTabHovering）。</p>
 *
 * <p>1.21.1 签名：mouseClicked(double,double,int)、checkTabHovering(GuiGraphics,
 * CreativeModeTab,int,int)。渲染不重复挂——平台 after-render 天然在整屏之后。</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerMouseClicked(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        if (PinOverlayManager.handleMouseClicked(mouseX, mouseY, button, screen)) {
            cir.setReturnValue(true);
            return;
        }
        if (RecipeViewerOverlay.mouseClicked(mouseX, mouseY, button, screen)) {
            cir.setReturnValue(true);
        }
    }

    /** While the query UI owns the cursor (viewer box / preview / pin), the
     *  creative tab under it must not hover: no tooltip, no hand cursor. */
    @Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true)
    private void brbe$blockTabHoverUnderViewer(GuiGraphics gui, CreativeModeTab tab,
                                               int mouseX, int mouseY,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.modalMaskOwnsCursor(mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }
}
