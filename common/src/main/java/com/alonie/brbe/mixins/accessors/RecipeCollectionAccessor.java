package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(RecipeCollection.class)
public interface RecipeCollectionAccessor {
    @Accessor("craftable")
    Set<RecipeDisplayId> betterRecipeBook$getCraftable();

    @Accessor("fitsDimensions")
    Set<RecipeDisplayId> getFitsDimensions();

    @Accessor("selected")
    Set<RecipeDisplayId> betterRecipeBook$getSelected();
}
