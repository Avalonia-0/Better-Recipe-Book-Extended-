package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ImageButton.class)
public abstract class RemoveBookButton {
    @Final
    @Shadow
    protected WidgetSprites sprites;

    @Inject(at = @At("HEAD"), method = "extractContents", cancellable = true)
    public void renderContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (sprites == RecipeBookComponent.RECIPE_BUTTON_SPRITES) {
            ((ImageButton) (Object) this).visible = BetterRecipeBook.config.enableBook;
            if (!BetterRecipeBook.config.enableBook) {
                ci.cancel();
            }
        }
    }
}
