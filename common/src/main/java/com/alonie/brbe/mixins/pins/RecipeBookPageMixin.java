package com.alonie.brbe.mixins.pins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookPage.class)
public class RecipeBookPageMixin {

    @Shadow private Minecraft minecraft;

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    public void mouseClicked(MouseButtonEvent event, int x, int y, int width, int height, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        // If the recipe page consumed the click, clear any focused recipe-book search box.
        if (cir.getReturnValue() && minecraft.screen != null && minecraft.screen.getFocused() instanceof EditBox searchBox) {
            searchBox.setFocused(false);
        }
    }

}
