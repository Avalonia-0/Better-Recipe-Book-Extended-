package com.alonie.brbe.mixins.toasts;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeToast.class)
public class RemoveToasts {
    @Inject(at = @At("HEAD"), method = "extractRenderState", cancellable = true)
    private void draw(GuiGraphicsExtractor gui, Font font, long startTime, CallbackInfo ci) {
        if (BetterRecipeBook.config.newRecipes.unlockAll) {
            ci.cancel();
        }
    }
}
