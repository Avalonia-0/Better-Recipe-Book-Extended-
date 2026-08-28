package com.alonie.brbe.mixins;

import com.alonie.brbe.util.TopLayerOverlayRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen.render TAIL 钩子——只保留顶层层绘制。
 *
 * <p>注意：查询浮层（RecipeViewerOverlay）<b>不在这里渲染</b>——Screen.render TAIL
 * 在容器屏幕自身的槽位/配方书绘制<b>之前</b>执行，浮层会被下层内容盖住（2026-08-28
 * 实测：R 键能打开但面板被背包/配方书遮挡，表现为"无法使用"）。查询浮层改由平台
 * after-render 钩子（fabric ScreenEvents.afterRender / neoforge ScreenEvent.Render.Post）
 * 在整屏渲染完成后绘制（见各端入口的 TopLayerOverlayRenderer.renderViewer 注册）。</p>
 */
@Mixin(Screen.class)
public abstract class ScreenRenderMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void brbe$renderTopLayerOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TopLayerOverlayRenderer.render((Screen) (Object) this, guiGraphics, mouseX, mouseY, partialTick);
    }
}
