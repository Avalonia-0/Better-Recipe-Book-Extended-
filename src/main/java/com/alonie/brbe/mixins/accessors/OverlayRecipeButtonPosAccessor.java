package com.alonie.brbe.mixins.accessors;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton$Pos")
public interface OverlayRecipeButtonPosAccessor {
    @Accessor("x")
    int brbe$getX();

    @Accessor("y")
    int brbe$getY();

    @Accessor("ingredients")
    List<ItemStack> brbe$getIngredients();

    @Invoker("selectIngredient")
    ItemStack brbe$selectIngredient(int index);
}
