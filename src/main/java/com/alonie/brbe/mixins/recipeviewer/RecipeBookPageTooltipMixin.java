package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla suppresses the hovered recipe-book button tooltip while the
 * alternative-recipe overlay is open ({@code RecipeBookPage.extractTooltip}
 * skips when {@code recipeBookPage.overlay.isVisible()}).  The standalone BRBE
 * viewer overlay is a separate {@code OverlayRecipeComponent} instance, so that
 * check no longer fires — restore the suppression while the viewer is active.
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageTooltipMixin {

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void brbe$suppressBookTooltipWhileViewer(GuiGraphics gui, int mouseX, int mouseY,
                                                     CallbackInfo ci) {
        if (RecipeViewerIndex.isViewerActive()) {
            ci.cancel();
        }
    }
}
