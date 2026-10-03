package com.alonie.brbe.interfaces;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;

public interface TopLayerOverlayProvider {
    boolean brbe$hasTopLayerOverlay();

    void brbe$renderTopLayerOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    /**
     * 顶层浮层这一遍的**格子 tooltip**（默认什么都不画）。
     *
     * <p>组浮层在 Fabric {@code ScreenEvents.afterRender}（帧末 tooltip 刷新**之后**）被再画
     * 一遍，浮层格子的 tooltip 必须跟着在它之后就地画——否则会被这一遍的浮层面板压住
     * （用户 2026-09-28 反馈）。见 {@link com.alonie.brbe.util.ClientCompat#drawComponentTooltipNow}。</p>
     */
    default void brbe$renderTopLayerTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    boolean brbe$clickTopLayerOverlay(MouseButtonEvent event, boolean doubleClick);

    ScreenRectangle brbe$getTopLayerOverlayBounds();
}
