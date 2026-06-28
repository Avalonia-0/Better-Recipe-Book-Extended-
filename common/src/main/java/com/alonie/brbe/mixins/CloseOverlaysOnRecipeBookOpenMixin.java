package com.alonie.brbe.mixins;

import com.alonie.brbe.mixins.accessors.AbstractContainerScreenAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Repositions the parent inventory screen when the recipe book opens so
 * that attached buttons (recipe-book toggle, mod overlays) move with the
 * inventory instead of staying at stale coordinates from the closed-book
 * layout.
 *
 * IMPORTANT: Must inject at RETURN, not HEAD.  At HEAD {@code this.visible}
 * is still {@code false}, so {@code updateScreenPosition()} returns the
 * <em>closed-book</em> layout position ({@code width - backgroundWidth})
 * instead of the open-book position ({@code width/2 - 86}).  At RETURN
 * {@code setVisible} has already flipped the flag and called
 * {@code initVisuals()}, so the calculation is correct.
 */
@Mixin(RecipeBookComponent.class)
public abstract class CloseOverlaysOnRecipeBookOpenMixin {

    @Shadow private boolean widthTooNarrow;
    @Shadow protected Minecraft minecraft;

    @Inject(method = "setVisible", at = @At("RETURN"))
    private void brbe$repositionOnOpen(boolean becomingVisible, CallbackInfo ci) {
        if (!becomingVisible) return;
        if (minecraft == null || minecraft.screen == null) return;
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        var acc = (AbstractContainerScreenAccessor) containerScreen;
        int newLeft = ((RecipeBookComponentAccessor) this)
                .updateScreenPositionInvoker(
                        containerScreen.width, acc.getImageWidth());
        acc.setLeftPos(newLeft);
    }
}
