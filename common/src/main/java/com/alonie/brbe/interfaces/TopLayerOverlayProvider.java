package com.alonie.brbe.interfaces;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public interface TopLayerOverlayProvider {
    boolean brbe$hasTopLayerOverlay();

    void brbe$renderTopLayerOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    boolean brbe$clickTopLayerOverlay(double mouseX, double mouseY, int button);

    ScreenRectangle brbe$getTopLayerOverlayBounds();
}
