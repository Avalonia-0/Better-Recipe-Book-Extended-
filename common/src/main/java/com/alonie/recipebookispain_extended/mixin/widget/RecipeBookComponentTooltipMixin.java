package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.recipebookispain_extended.access.CreativeTabButtonAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Renders CreativeModeTab display-name tooltips when the mouse hovers
 * over RBIP creative tab buttons.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentTooltipMixin {

    @Shadow
    @Final
    private List<RecipeBookTabButton> tabButtons;

    @Shadow
    protected Minecraft minecraft;

    @SuppressWarnings("unused")
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void rbip$renderCreativeTabTooltip(GuiGraphics gui, int x, int y,
                                                int mouseX, int mouseY, CallbackInfo ci) {
        if (minecraft == null || minecraft.screen == null) return;

        for (RecipeBookTabButton btn : this.tabButtons) {
            if (!btn.visible || !btn.isMouseOver(mouseX, mouseY)) continue;
            if (!(btn instanceof CreativeTabButtonAccess access)) continue;

            CreativeModeTab tab = access.rbip$getCreativeTab();
            if (tab != null) {
                gui.setTooltipForNextFrame(minecraft.font, tab.getDisplayName(), mouseX, mouseY);
                ci.cancel();
                return;
            }
        }
    }
}
