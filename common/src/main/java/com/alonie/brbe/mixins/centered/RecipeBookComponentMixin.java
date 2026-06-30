package com.alonie.brbe.mixins.centered;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.layout.BookLayout;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin {
    @Shadow private int xOffset;
    @Shadow private boolean widthTooNarrow;

    @Inject(
            method = "initVisuals",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;xOffset:I")
    )
    public void center(CallbackInfo ci) {
        if (BetterRecipeBook.ctx().config().keepCentered) {
            this.xOffset = this.widthTooNarrow ? 0 : BookLayout.X_OFFSET_CENTERED;
        } else {
            this.xOffset = this.widthTooNarrow ? 0 : BookLayout.X_OFFSET_STANDARD;
        }
    }

    @Inject(method = "updateScreenPosition", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "RETURN"), cancellable = true)
    public void findLeftEdge(int width, int backgroundWidth, CallbackInfoReturnable<Integer> cir, int j) {
        if (BetterRecipeBook.ctx().config().keepCentered) {
            j = (width - backgroundWidth) / 2;
        }
        cir.setReturnValue(j);
    }

    @Inject(method = "isOffsetNextToMainGUI", at = @At("RETURN"), cancellable = true)
    public void isWide(CallbackInfoReturnable<Boolean> cir) {
        if (this.xOffset == BookLayout.X_OFFSET_CENTERED || this.xOffset == BookLayout.X_OFFSET_STANDARD) {
            cir.setReturnValue(true);
        }
    }
}
