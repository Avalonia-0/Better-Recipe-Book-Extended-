package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public interface OverlayRecipeButtonAccessor {
    @Accessor("recipe")
    RecipeHolder<?> getRecipe();
}
