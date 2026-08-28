package com.alonie.brbe.util;

import com.alonie.brbe.interfaces.TopLayerOverlayProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

public final class TopLayerOverlayRenderer {
    private TopLayerOverlayRenderer() {
    }

    public static boolean hasOverlay(Screen screen) {
        if (screen instanceof TopLayerOverlayProvider provider) {
            return provider.brbe$hasTopLayerOverlay();
        }

        return false;
    }

    public static void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (screen instanceof TopLayerOverlayProvider provider) {
            if (provider.brbe$hasTopLayerOverlay()) {
                provider.brbe$renderTopLayerOverlay(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    /** 查询浮层：平台 after-render 钩子（整屏渲染完成后、最顶层）调用点。
     *  与 {@link #render} 的 Screen TAIL 不同——那里在容器槽位/配方书绘制之前执行，
     *  浮层会被盖住。 */
    public static void renderViewer(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        com.alonie.brbe.util.RecipeViewerOverlay.render(guiGraphics, mouseX, mouseY, partialTick);
        com.alonie.brbe.util.RecipeViewerOverlay.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    public static ScreenRectangle getOverlayBounds(Screen screen) {
        if (screen instanceof TopLayerOverlayProvider provider) {
            return provider.brbe$getTopLayerOverlayBounds();
        }
        return null;
    }
}
