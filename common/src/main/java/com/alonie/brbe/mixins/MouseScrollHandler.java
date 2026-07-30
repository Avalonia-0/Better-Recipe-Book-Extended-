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

    @Inject(at = @At("HEAD"), method = "onScroll")
    public void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (vertical != 0 && BetterRecipeBook.getQueuedScroll() == 0) {
            BetterRecipeBook.setQueuedScroll(vertical > 0 ? -1 : 1);
        }
    }
}
