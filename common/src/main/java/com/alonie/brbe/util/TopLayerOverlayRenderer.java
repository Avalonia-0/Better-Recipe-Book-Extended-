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
     *  浮层会被盖住。渲染走 PinOverlayManager（z 序交错 pin 与 viewer；
     *  viewer 的 tooltip 在自身 render 末尾画）。 */
    public static void renderViewer(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 整层绘制在 GUI z 之上：容器槽位/配方书内容经 renderItem 以 z=150 写入，
        // 而本浮层面板用 blitSprite(z=0)、图标用 renderItem(z=150)——GUI 深度测试下
        // 会被下层内容盖住（实测"查询界被物品栏/配方书盖住"）。参考 1.21.11 用
        // nextStratum() 置顶；1.21.1 无该方法，改用 pose 基 z 抬高到所有项之上。
        guiGraphics.pose().pushPose();
        try {
            guiGraphics.pose().translate(0.0F, 0.0F, 400.0F);
            com.alonie.brbe.pinoverlay.PinOverlayManager.render(guiGraphics, mouseX, mouseY, partialTick);
            com.alonie.brbe.util.RecipeViewerOverlay.renderTooltip(guiGraphics, mouseX, mouseY);
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    public static ScreenRectangle getOverlayBounds(Screen screen) {
        if (screen instanceof TopLayerOverlayProvider provider) {
            return provider.brbe$getTopLayerOverlayBounds();
        }
        return null;
    }
}
