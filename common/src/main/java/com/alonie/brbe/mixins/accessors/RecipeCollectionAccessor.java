package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(RecipeCollection.class)
public interface RecipeCollectionAccessor {
    @Accessor("fitsDimensions")
    Set<RecipeHolder<?>> getFitsDimensions();
}
