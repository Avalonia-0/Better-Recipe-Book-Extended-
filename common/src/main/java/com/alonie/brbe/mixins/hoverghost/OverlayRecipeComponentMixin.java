package com.alonie.brbe.mixins.hoverghost;

import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.HoverGhostRecipe;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * **展开的替代配方组**浮层的悬停幽灵预览（用户 2026-09-25 诉求）。
 *
 * <p>浮层打开时页按钮已经不参与命中（浮层盖在上面），所以命中和释放都由这里负责：
 * 组内任一按钮悬停 → 该**具体变体**的幽灵物品进工作区；都不命中 → 释放。</p>
 *
 * <p>本分支的浮层按钮没有"悬停放大预览"（那是 26.x/1.21.11 的 BRBE 弹窗），所以
 * 这里无需移除任何界面，只补幽灵物品。</p>
 */
@Mixin(OverlayRecipeComponent.class)
public abstract class OverlayRecipeComponentMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$hoverGhostPreview(GuiGraphics gui, int mouseX, int mouseY,
                                        float a, CallbackInfo ci) {
        OverlayRecipeComponent self = (OverlayRecipeComponent) (Object) this;
        if (!self.isVisible()) {
            return;
        }
        if (RecipeViewerOverlay.modalMaskOwnsCursor(mouseX, mouseY)) {
            // LEI 查询窗口 / 预览弹窗 / pin 挡住指针时不判定（与 CycleLock 屏幕层同口径）
            HoverGhostRecipe.release();
            return;
        }
        for (AbstractWidget button : ((OverlayRecipeComponentAccessor) self).getRecipeButtons()) {
            if (!button.isHoveredOrFocused()) {
                continue;
            }
            HoverGhostRecipe.hover(HoverGhostRecipe.currentBook(), button,
                    ((OverlayRecipeButtonAccessor) button).getRecipe());
            return;
        }
        HoverGhostRecipe.release();
    }
}
