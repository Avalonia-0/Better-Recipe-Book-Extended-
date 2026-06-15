package com.alonie.recipebookispain_extended.mixin;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures mouse scroll events for RBIP tab pagination.
 * The scroll direction is stored globally and consumed by
 * {@code RecipeBookWidgetMixin} during the next render tick.
 */
@Mixin(MouseHandler.class)
public abstract class RbipMouseScrollMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(at = @At("HEAD"), method = "onScroll")
    private void rbip$captureScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window != this.minecraft.getWindow().getWindow()) return;
        if (vertical == 0) return;

        // Store scroll direction: positive = scroll up, negative = scroll down
        RecipeBookIsPain.rbip$queueScroll(vertical > 0 ? 1 : -1);
    }
}
