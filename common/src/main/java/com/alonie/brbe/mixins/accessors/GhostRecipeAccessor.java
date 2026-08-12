package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(GhostRecipe.class)
public interface GhostRecipeAccessor {

    @Accessor("ingredients")
    List<GhostRecipe.GhostIngredient> getIngredients();
}
