package com.alonie.brbe.util;

import com.alonie.brbe.interfaces.IOverlayCellTooltip;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 原版配方书（合成台 / 熔炉系）**替代配方组浮层格子**的 tooltip：找出指针下的那格、
 * 取它的行内容。用户 2026-09-27 诉求（组内配方要有 tooltip），
 * 行内容由 {@link IOverlayCellTooltip}（{@code OverlayRecipeButtonMixin} 实现）给出。
 *
 * <p>两个使用点：① 常规提取遍（{@code RecipeBookPage.extractTooltip} / 1.21.x
 * {@code renderTooltip}）里注册延迟 tooltip；② {@link TopLayerOverlayRenderer} 的
 * **顶层重绘那一遍**里就地画——组浮层会在那一遍被再画一次（在帧末 tooltip 刷新之后），
 * 所以格子 tooltip 必须跟着在那里画，否则被浮层压住。</p>
 */
public final class OverlayCellTooltips {

    private OverlayCellTooltips() {
    }

    /**
     * 指针被 pin / 查询窗口盖住时不该出格子 tooltip（浮层不该"穿透"到下面的配方书）。
     *
     * <p>顶层那一遍的绘制跑在 pin/查询窗口之后，所以这条判据必须在那里自己再判一次
     * ——否则一个盖住指针的 pin 会被格子 tooltip 反过来压住。</p>
     */
    public static boolean blockedByOverlay(int mouseX, int mouseY) {
        return PinOverlayManager.covers(mouseX, mouseY)
                || RecipeViewerOverlay.modalMaskOwnsCursor(mouseX, mouseY);
    }

    /** 浮层里被悬停格子的 tooltip 行；{@code null} = 没有悬停任何格子（或该格无可显示内容）。 */
    @Nullable
    public static List<Component> hoveredLines(@Nullable OverlayRecipeComponent overlay) {
        if (overlay == null || !overlay.isVisible()) {
            return null;
        }

        for (AbstractWidget button : ((OverlayRecipeComponentAccessor) overlay).getRecipeButtons()) {
            if (!button.isHoveredOrFocused()) {
                continue;
            }
            if (!(button instanceof IOverlayCellTooltip cell)) {
                return null;
            }
            return cell.brbe$cellTooltip();
        }

        return null;
    }
}
