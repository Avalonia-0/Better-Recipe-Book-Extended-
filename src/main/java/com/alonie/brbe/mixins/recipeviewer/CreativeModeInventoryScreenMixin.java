package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The creative inventory screen draws its own tab strip AFTER calling
 * {@code super.render}, so the BRBE R/U viewer (rendered at the
 * container's RETURN hook) would sit UNDER the tabs, and the screen's own
 * {@code mouseClicked} override processes the tab clicks before the viewer can
 * consume them — the creative tabs "leak through" the query interface, the
 * preview and the pin overlays.
 *
 * <p>This mixin re-hosts the viewer on the creative screen: clicks are routed
 * to the viewer first (like the recipe-book screens), the viewer renders on
 * top of the tab strip, and the tabs' hover tooltip ({@code checkTabHovering})
 * is suppressed while the query UI owns the cursor.</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                         CallbackInfoReturnable<Boolean> cir) {
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        if (PinOverlayManager.handleMouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
            return;
        }
        if (RecipeViewerOverlay.mouseClicked(event, doubleClick, screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$viewerRender(GuiGraphics gui, int mouseX, int mouseY,
                                   float delta, CallbackInfo ci) {
        PinOverlayManager.render(gui, mouseX, mouseY, delta);
    }

    /** While the query UI owns the cursor (viewer box / preview / pin), the
     *  creative tab under it must not hover: no tooltip, no hand cursor. */
    @Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true)
    private void brbe$blockTabHoverUnderViewer(GuiGraphics gui, CreativeModeTab tab,
                                               int mouseX, int mouseY,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.modalMaskOwnsCursor(mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }
}
