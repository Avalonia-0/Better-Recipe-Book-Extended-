package com.alonie.brbe.interfaces;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;

public interface TopLayerOverlayProvider {
    boolean betterRecipeBook$hasTopLayerOverlay();

    void betterRecipeBook$renderTopLayerOverlay(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick);

    boolean betterRecipeBook$clickTopLayerOverlay(MouseButtonEvent event, boolean doubleClick);

    ScreenRectangle betterRecipeBook$getTopLayerOverlayBounds();
}
