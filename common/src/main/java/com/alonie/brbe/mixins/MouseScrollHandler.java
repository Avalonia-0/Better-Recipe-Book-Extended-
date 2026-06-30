package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseScrollHandler {
    @Final @Shadow
    private Minecraft minecraft;

    @Inject(at = @At(value = "RETURN"), method = "onScroll")
    public void onMouseScroll(long window, double arg1, double vertical, CallbackInfo ci) {
        if (BetterRecipeBook.getQueuedScroll() == 0 && BetterRecipeBook.ctx().config().scrolling.enableScrolling) {
            assert minecraft.player != null;

            double d = (this.minecraft.options.discreteMouseScroll().get() ? Math.signum(vertical) : vertical) * this.minecraft.options.mouseWheelSensitivity().get();
            BetterRecipeBook.setQueuedScroll((int) -Math.signum(d));
        }
    }
}
