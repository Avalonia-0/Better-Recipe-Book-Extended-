package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public interface OverlayRecipeButtonAccessor {

    @Accessor("this$0")
    OverlayRecipeComponent brbe$getOuterComponent();

    @Accessor("recipe")
    RecipeDisplayId brbe$getRecipe();
}
