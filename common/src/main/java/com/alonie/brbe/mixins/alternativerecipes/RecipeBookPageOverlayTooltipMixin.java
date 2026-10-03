package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.interfaces.IOverlayCellTooltip;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 原版配方书（合成台 / 熔炉系的 {@code RecipeBookPage}）**替代配方组浮层格子**的 tooltip
 * （用户 2026-09-27 诉求：展开后的组内配方要像普通配方格那样有 tooltip）。
 *
 * <p>为什么挂在 {@code renderTooltip} 而不是格子自己的渲染里：原版这个方法才是配方书
 * tooltip 的正规出口——容器槽位的 tooltip（{@code AbstractContainerScreen.renderTooltip}）
 * 先注册，配方书这一遍后注册、会把它盖掉；格子渲染期间注册的 tooltip 反而会被槽位 tooltip
 * 覆盖。原版在这里的判定是 {@code hoveredButton != null && !overlay.isVisible()}，也就是
 * **浮层打开时什么都不画**——于是格子的 tooltip 一直缺着。这里补上。</p>
 *
 * <p>格子行内容来自 {@link IOverlayCellTooltip}（{@code OverlayRecipeButtonMixin} 实现：
 * 与普通配方格同款）。查询窗口的浮层另有自己的 tooltip 通路（{@code RecipeViewerOverlay}），
 * pin 也是——两者盖住指针时这里一律不出（与 {@code RecipeBookPageTooltipMixin} 同一语义）。</p>
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageOverlayTooltipMixin {

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void brbe$overlayCellTooltip(GuiGraphics gui, int mouseX, int mouseY, CallbackInfo ci) {
        // 与原版同款前置条件（它自己也要 screen != null 才画）
        if (Minecraft.getInstance().screen == null) {
            return;
        }
        // 查询窗口 / pin 盖住指针时不出 tooltip（浮层不该"穿透"到底下的配方书）
        if (RecipeViewerIndex.isViewerActive()) {
            return;
        }
        if (PinOverlayManager.covers(mouseX, mouseY)) {
            return;
        }

        OverlayRecipeComponent overlay = ((RecipeBookPageAccessor) this).getOverlay();
        if (overlay == null || !overlay.isVisible()) {
            return;
        }

        for (AbstractWidget button : ((OverlayRecipeComponentAccessor) overlay).getRecipeButtons()) {
            if (!button.isHoveredOrFocused()) {
                continue;
            }
            if (!(button instanceof IOverlayCellTooltip cell)) {
                return;
            }
            List<Component> lines = cell.brbe$cellTooltip();
            if (lines != null && !lines.isEmpty()) {
                gui.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
                // 浮层打开时原版本来就不画 tooltip；这里连"格子底下那格"的 tooltip 一并挡掉
                ci.cancel();
            }
            return;
        }
    }
}
