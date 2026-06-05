package com.alonie.brbe.mixins.accessors;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton$Pos")
public interface OverlayRecipeButtonPosAccessor {
    @Accessor("x")
    int betterRecipeBook$getX();

    @Accessor("y")
    int betterRecipeBook$getY();

    @Accessor("ingredients")
    ItemStack[] betterRecipeBook$getIngredients();
}
