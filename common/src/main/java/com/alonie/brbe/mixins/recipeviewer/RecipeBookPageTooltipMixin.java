package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 版配方书按钮 tooltip 抑制（1.21.11 RecipeBookPageTooltipMixin 移植）。
 *
 * <p>vanilla 只在自家替代配方 overlay 可见时抑制悬停按钮 tooltip；独立 viewer
 * 实例不触发该检查——viewer 打开时配方书悬停 tooltip 穿透，恢复抑制。</p>
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageTooltipMixin {

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void brbe$suppressBookTooltipWhileViewer(GuiGraphics gui, int mouseX, int mouseY,
                                                     CallbackInfo ci) {
        if (RecipeViewerIndex.isViewerActive()) {
            ci.cancel();
        }
    }
}
