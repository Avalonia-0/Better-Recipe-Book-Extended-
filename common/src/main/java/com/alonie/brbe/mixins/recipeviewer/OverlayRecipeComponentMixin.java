package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 版 OverlayRecipeComponent 守卫（1.21.11 OverlayRecipeComponentMixin 移植）。
 *
 * <p>① {@code setVisible(false)} 守卫：viewer 打开期间任何非授权关闭
 * （ESC/框外点击两条合规路径都会先清 viewerActive 再 setVisible(false)）被取消；
 * ② viewer 分页盒由 RecipeViewerOverlay 全权绘制——跳过 vanilla render pass；
 * ③ 非 viewer 实例把 hovered 按钮最后重画（2x 放大盖过邻钮）。</p>
 */
@Mixin(OverlayRecipeComponent.class)
public abstract class OverlayRecipeComponentMixin {

    @Inject(method = "setVisible", at = @At("HEAD"), cancellable = true)
    private void brbe$keepViewerOverlay(boolean visible, CallbackInfo ci) {
        if (!visible && RecipeViewerIndex.isViewerActive()) {
            ci.cancel();
        }
    }

    /** The viewer box is drawn entirely by {@code RecipeViewerOverlay}: skip the
     *  vanilla draw pass for ANY active viewer overlay, not only paged ones.
     *  <p>2026-09-11：守卫原先要求 {@code isPaged()}，于是单页窗口（≤50 条结果）
     *  时 vanilla 还会自己画一个"最多 5 列"的背景盒子（133px 宽 ×
     *  {@code ceil(n/5)*25+8} 高）——结果数 26…50 时它比查询窗口更高，其下边框与
     *  左边框列（正好是工作站列与主体交界处的 x）会从窗口下缘露出来，看起来就像
     *  "交界处偏了半个像素"，逼得人去反复微调 column_panel.png 的像素（无效——那条
     *  多余边来自这个 vanilla 画的第二个盒子）。 */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void brbe$skipOwnOverlayRender(GuiGraphics gui, int mouseX, int mouseY,
                                           float delta, CallbackInfo ci) {
        OverlayRecipeComponent self = (OverlayRecipeComponent) (Object) this;
        if (RecipeViewerOverlay.isOwnActiveOverlay(self)) {
            ci.cancel();
        }
    }

    /** Re-draw the hovered button last so the 2x hover enlargement paints on
     *  top of the neighbouring buttons (vanilla alternative-overlay path; the
     *  viewer instance does its own hover re-draw after the category tabs). */
    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$redrawHoveredOnTop(GuiGraphics gui, int mouseX, int mouseY,
                                         float delta, CallbackInfo ci) {
        OverlayRecipeComponent self = (OverlayRecipeComponent) (Object) this;
        if (!self.isVisible()) return;
        if (RecipeViewerOverlay.isOwnOverlay(self)) return;
        for (AbstractWidget widget : ((OverlayRecipeComponentAccessor) self).getRecipeButtons()) {
            if (widget.isHoveredOrFocused()) {
                widget.render(gui, mouseX, mouseY, delta);
                return;
            }
        }
    }
}
