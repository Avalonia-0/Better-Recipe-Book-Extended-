package com.alonie.brbe.mixins;

import com.alonie.brbe.interfaces.RecipeBookTabButtonIconOffset;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RecipeBookTabButton.class)
public abstract class RecipeBookTabButtonMixin implements RecipeBookTabButtonIconOffset {
    @Unique
    private int betterRecipeBook$iconYOffset;

    @ModifyArg(
            method = "extractIcon",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;II)V"
            ),
            index = 2
    )
    private int betterRecipeBook$offsetIconY(int y) {
        return y + this.betterRecipeBook$iconYOffset;
    }

    @Override
    public void betterRecipeBook$setIconYOffset(int iconYOffset) {
        this.betterRecipeBook$iconYOffset = iconYOffset;
    }
}
