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
            }
            return;
        }

        OverlayRecipeComponent overlay = getVanillaOverlay(screen);
        if (overlay != null) {
            guiGraphics.nextStratum();
            overlay.render(guiGraphics, mouseX, mouseY, partialTick);
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
