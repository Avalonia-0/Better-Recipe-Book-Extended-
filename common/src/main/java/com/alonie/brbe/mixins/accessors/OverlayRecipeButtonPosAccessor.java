package com.alonie.brbe.mixins.accessors;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton$Pos")
public interface OverlayRecipeButtonPosAccessor {
    @Accessor("x")
    int betterRecipeBook$getX();

    @Accessor("y")
    int betterRecipeBook$getY();

    @Invoker("selectIngredient")
    ItemStack betterRecipeBook$selectIngredient(int index);
}
