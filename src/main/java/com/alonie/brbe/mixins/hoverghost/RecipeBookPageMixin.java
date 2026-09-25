package com.alonie.brbe.mixins.hoverghost;

import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.HoverGhostRecipe;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 配方书**页按钮**的悬停幽灵预览（用户 2026-09-25 诉求）。
 *
 * <p>挂在本方法 RETURN：原版在这里已经逐帧算好了 {@code hoveredButton}（渲染循环里
 * {@code if (button.visible && button.isHoveredOrFocused()) this.hoveredButton = button;}），
 * 我们直接复用它的命中结果，不再自己判一遍矩形——顺带天然支持翻页动画期间
 * {@code RecipeBookPageAnimationMixin} 覆盖过的"视觉命中的 snapshot 按钮"。</p>
 *
 * <p>命中按钮的配方取 {@code getCurrentRecipe()}——它返回的正是**当前轮循到**的那一条
 * （未展开的替代配方组按钮因此显示当前变体的幽灵物品）。展开的替代配方组浮层打开时
 * 页按钮整体让位（浮层的按钮才是命中目标，见 {@code OverlayRecipeComponentMixin}）。</p>
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$hoverGhostPreview(GuiGraphics gui, int xo, int yo,
                                        int mouseX, int mouseY, float a, CallbackInfo ci) {
        RecipeBookPageAccessor self = (RecipeBookPageAccessor) this;
        OverlayRecipeComponent overlay = self.getOverlay();
        if (overlay != null && overlay.isVisible()) {
            return;
        }
        if (RecipeViewerOverlay.modalMaskOwnsCursor(mouseX, mouseY)) {
            // LEI 查询窗口 / 预览弹窗 / pin 挡住指针时不判定（与 CycleLock 屏幕层同口径）
            HoverGhostRecipe.release();
            return;
        }
        RecipeButton hovered = self.getHoveredButton();
        HoverGhostRecipe.hover(self.brbe$getParent(), hovered,
                hovered == null ? null : HoverGhostRecipe.displayOf(hovered));
    }
}
