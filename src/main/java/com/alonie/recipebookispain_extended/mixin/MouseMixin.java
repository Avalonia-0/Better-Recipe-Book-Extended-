package com.alonie.recipebookispain_extended.mixin;

import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import com.alonie.recipebookispain_extended.mixin.screen.RecipeBookScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow @Final private Minecraft minecraft;

    @Shadow
    private double getScaledXPos(Window window) {
        throw new AssertionError();
    }

    @Shadow
    private double getScaledYPos(Window window) {
        throw new AssertionError();
    }

    @Inject(at = @At("HEAD"), method = "onScroll", cancellable = true)
    private void rbip$scrollRecipeBookTabs(long windowHandle, double horizontalAmount, double verticalAmount, CallbackInfo ci) {
        if (windowHandle != this.minecraft.getWindow().handle()
                || this.minecraft.gui.overlay() != null
                || !(this.minecraft.gui.screen() instanceof AbstractRecipeBookScreen<?> screen)) {
            return;
        }

        Window window = this.minecraft.getWindow();
        double mouseX = this.getScaledXPos(window);
        double mouseY = this.getScaledYPos(window);
        double scaledVerticalAmount = this.rbip$scaleScrollAmount(verticalAmount);

        RecipeBookComponent<?> recipeBook = ((RecipeBookScreenAccessor) screen).rbip$getRecipeBook();
        if (((RecipeBookScrollAccess) recipeBook).rbip$scrollPages(mouseX, mouseY, scaledVerticalAmount)) {
            screen.afterMouseAction();
            ci.cancel();
        }
    }

    private double rbip$scaleScrollAmount(double amount) {
        double scaledAmount = this.minecraft.options.discreteMouseScroll().get()
                ? Math.signum(amount)
                : amount;
        return scaledAmount * this.minecraft.options.mouseWheelSensitivity().get();
    }
}
