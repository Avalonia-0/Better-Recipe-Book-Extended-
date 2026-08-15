package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the BRBE R/U viewer overlay open: the viewer must only close via the two
 * sanctioned paths (ESC, or a click outside the box) — and both clear
 * {@code viewerActive} <em>before</em> calling {@code setVisible(false)}.  So
 * any {@code setVisible(false)} observed while the viewer is still active is an
 * unsanctioned close and is cancelled.  When the viewer is inactive this is a
 * passthrough, so the vanilla alternative-recipe-group behaviour is unchanged.
 */
@Mixin(OverlayRecipeComponent.class)
public abstract class OverlayRecipeComponentMixin {

    @Inject(method = "setVisible", at = @At("HEAD"), cancellable = true)
    private void brbe$keepViewerOverlay(boolean visible, CallbackInfo ci) {
        if (!visible && RecipeViewerIndex.isViewerActive()) {
            ci.cancel();
        }
    }

    /** The paged viewer box is drawn entirely by {@code RecipeViewerOverlay}
     *  (vanilla lays out at most 5 columns); skip the vanilla draw pass. */
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void brbe$skipPagedRender(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                      float delta, CallbackInfo ci) {
        OverlayRecipeComponent self = (OverlayRecipeComponent) (Object) this;
        if (RecipeViewerOverlay.isOwnOverlay(self) && RecipeViewerOverlay.isPaged()) {
            ci.cancel();
        }
    }

    /** Re-draw the hovered button last so the 2x hover enlargement paints on
     *  top of the neighbouring buttons instead of being covered by them.  The
     *  RETURN injector also runs on the early {@code !isVisible} return, so
     *  guard on visibility to avoid drawing the enlarged button after ESC has
     *  dismissed the overlay.  The standalone viewer overlay is skipped here —
     *  its hovered button is re-drawn by {@code RecipeViewerOverlay.render}
     *  after the category tabs so it paints above them. */
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void brbe$redrawHoveredOnTop(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                         float delta, CallbackInfo ci) {
        OverlayRecipeComponent self = (OverlayRecipeComponent) (Object) this;
        if (!self.isVisible()) return;
        if (RecipeViewerOverlay.isOwnOverlay(self)) return;
        for (AbstractWidget widget : ((OverlayRecipeComponentAccessor) self).getRecipeButtons()) {
            if (widget.isHoveredOrFocused()) {
                widget.extractRenderState(gui, mouseX, mouseY, delta);
                return;
            }
        }
    }
}
