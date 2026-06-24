package com.alonie.brbe.interfaces;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;

public interface TopLayerOverlayProvider {
    boolean brbe$hasTopLayerOverlay();

    void brbe$renderTopLayerOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    boolean brbe$clickTopLayerOverlay(MouseButtonEvent event, boolean doubleClick);

    ScreenRectangle brbe$getTopLayerOverlayBounds();
}
