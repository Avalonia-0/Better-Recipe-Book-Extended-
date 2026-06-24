package com.alonie.brbe.mixins.accessors;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton$Pos")
public interface OverlayRecipeButtonPosAccessor {
    @Accessor("x")
    int brbe$getX();

    @Accessor("y")
    int brbe$getY();

    @Accessor("ingredients")
    ItemStack[] brbe$getIngredients();
}
