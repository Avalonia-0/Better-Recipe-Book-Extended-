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

    /** The paged viewer box is drawn entirely by {@code RecipeViewerOverlay}:
     *  skip the vanilla draw pass for the viewer instance when paged. */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void brbe$skipPagedRender(GuiGraphics gui, int mouseX, int mouseY,
                                      float delta, CallbackInfo ci) {
        OverlayRecipeComponent self = (OverlayRecipeComponent) (Object) this;
        if (RecipeViewerOverlay.isOwnOverlay(self) && RecipeViewerOverlay.isPaged()) {
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
