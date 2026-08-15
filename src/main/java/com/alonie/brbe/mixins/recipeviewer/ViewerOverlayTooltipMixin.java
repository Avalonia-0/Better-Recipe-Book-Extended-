package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the recipe-output tooltip for the BRBE R/U viewer's alternative-recipe
 * group.  Injects at {@code OverlayRecipeComponent.extractRenderState} RETURN so
 * the tooltip paints on top of the box (last-drawn-in-node).
 *
 * <p>The standalone viewer box is drawn by {@code RecipeViewerOverlay.render}
 * with the tooltip rendered last (after the category tabs and the hovered
 * button), so the tooltip is skipped here for the viewer instance — this mixin
 * only handles vanilla recipe-book overlays.</p>
 */
@Mixin(OverlayRecipeComponent.class)
public abstract class ViewerOverlayTooltipMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void brbe$viewerOverlayTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                           float delta, CallbackInfo ci) {
        if (!RecipeViewerIndex.isViewerActive()) return;
        if (RecipeViewerOverlay.isOwnOverlay((OverlayRecipeComponent) (Object) this)) return;
        RecipeViewerOverlay.renderTooltip(gui, mouseX, mouseY);
    }
}
