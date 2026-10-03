package com.alonie.brbe.util;

import com.alonie.brbe.interfaces.TopLayerOverlayProvider;
import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TopLayerOverlayRenderer {
    private TopLayerOverlayRenderer() {
    }

    public static boolean hasOverlay(Screen screen) {
        if (screen instanceof TopLayerOverlayProvider provider) {
            return provider.brbe$hasTopLayerOverlay();
        }

        return getVanillaOverlay(screen) != null;
    }

    /**
     * 该屏幕的组浮层是否会被 {@link #render} 在 {@code ScreenEvents.afterRender} 里**再画一遍**。
     *
     * <p>那一遍在帧末的 tooltip 刷新（{@code Screen.renderWithTooltipAndSubtitles} 里的
     * {@code GuiGraphics.renderDeferredElements}）**之后**执行（1.21.11 的 Fabric
     * {@code afterRender} 由 {@code GameRendererMixin} 包在该方法外层），所以受影响的 tooltip
     * （浮层格子的 tooltip）不能再走延迟注册——必须改到那一遍里就地画，否则会被重画的浮层
     * 压住（用户 2026-09-28 反馈）。判定条件与 {@link #render} 的两个分支严格一致。</p>
     */
    public static boolean redrawsOverlayOnTop(@Nullable Screen screen) {
        if (screen == null || RecipeViewerOverlay.isActive()) {
            return false;
        }
        if (screen instanceof TopLayerOverlayProvider provider) {
            return provider.brbe$hasTopLayerOverlay();
        }

        return getVanillaOverlay(screen) != null;
    }

    public static void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // A BRBE query window is the top-most layer.  This hook runs at the END
        // of the frame (Fabric's after-render event) and raises the stratum
        // itself, so an unconditional host-group draw lands ABOVE the window —
        // which the screen's own RETURN hook drew earlier (2026-09-13 user
        // report: the alternative-recipe group covered the query window).
        // While a window is open the group stays where the book page drew it,
        // one stratum below the window: still visible wherever the window does
        // not cover it, and the window wins where it does.
        if (RecipeViewerOverlay.isActive()) {
            return;
        }

        if (screen instanceof TopLayerOverlayProvider provider) {
            if (provider.brbe$hasTopLayerOverlay()) {
                guiGraphics.nextStratum();
                provider.brbe$renderTopLayerOverlay(guiGraphics, mouseX, mouseY, partialTick);
                // 浮层格子的 tooltip 画在刚重画的浮层之上（帧末 tooltip 刷新已经过去，
                // 这里只能就地画）。用户 2026-09-28：格子 tooltip 被浮层压住。
                // pin / 查询窗口盖住指针时不出（它们在这一遍之前画过自己的 tooltip）。
                if (!OverlayCellTooltips.blockedByOverlay(mouseX, mouseY)) {
                    provider.brbe$renderTopLayerTooltip(guiGraphics, mouseX, mouseY);
                }
            }
            return;
        }

        OverlayRecipeComponent overlay = getVanillaOverlay(screen);
        if (overlay != null) {
            guiGraphics.nextStratum();
            overlay.render(guiGraphics, mouseX, mouseY, partialTick);
            // 原版书（合成台/熔炉系）同上：格子 tooltip 跟着顶层那一遍画。
            if (!OverlayCellTooltips.blockedByOverlay(mouseX, mouseY)) {
                ClientCompat.drawComponentTooltipNow(guiGraphics, OverlayCellTooltips.hoveredLines(overlay),
                        mouseX, mouseY);
            }
        }
    }

    public static ScreenRectangle getOverlayBounds(Screen screen) {
        if (screen instanceof TopLayerOverlayProvider provider) {
            return provider.brbe$getTopLayerOverlayBounds();
        }

        OverlayRecipeComponent overlay = getVanillaOverlay(screen);
        return overlay == null ? null : getVanillaOverlayBounds(overlay);
    }

    private static OverlayRecipeComponent getVanillaOverlay(Screen screen) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> recipeBookScreen)) {
            return null;
        }

        RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) recipeBookScreen).brbe$getRecipeBookComponent();
        if (!book.isVisible()) {
            return null;
        }

        OverlayRecipeComponent overlay = ((RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).getRecipeBookPage()).getOverlay();
        return overlay.isVisible() ? overlay : null;
    }

    private static ScreenRectangle getVanillaOverlayBounds(OverlayRecipeComponent overlay) {
        List<AbstractWidget> buttons = ((OverlayRecipeComponentAccessor) overlay).getRecipeButtons();
        if (buttons.isEmpty()) {
            return null;
        }

        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (AbstractWidget button : buttons) {
            left = Math.min(left, button.getX());
            top = Math.min(top, button.getY());
            right = Math.max(right, button.getX() + button.getWidth());
            bottom = Math.max(bottom, button.getY() + button.getHeight());
        }

        left -= 4;
        top -= 5;
        right += 5;
        bottom += 4;
        return new ScreenRectangle(left, top, right - left, bottom - top);
    }
}
