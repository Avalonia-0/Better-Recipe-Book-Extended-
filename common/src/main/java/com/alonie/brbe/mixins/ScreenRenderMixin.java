package com.alonie.brbe.mixins;

import com.alonie.brbe.util.TopLayerOverlayRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenRenderMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void brbe$renderTopLayerOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TopLayerOverlayRenderer.render((Screen) (Object) this, guiGraphics, mouseX, mouseY, partialTick);
    }
}
