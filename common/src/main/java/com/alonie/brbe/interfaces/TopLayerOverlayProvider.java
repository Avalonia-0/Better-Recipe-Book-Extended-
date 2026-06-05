package com.alonie.brbe.interfaces;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public interface TopLayerOverlayProvider {
    boolean betterRecipeBook$hasTopLayerOverlay();

    void betterRecipeBook$renderTopLayerOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    boolean betterRecipeBook$clickTopLayerOverlay(double mouseX, double mouseY, int button);

    ScreenRectangle betterRecipeBook$getTopLayerOverlayBounds();
}
