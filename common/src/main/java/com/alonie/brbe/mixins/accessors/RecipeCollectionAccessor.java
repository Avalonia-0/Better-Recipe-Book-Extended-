package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(RecipeCollection.class)
public interface RecipeCollectionAccessor {
    @Accessor("fitsDimensions")
    Set<RecipeHolder<?>> getFitsDimensions();

    @Accessor("craftable")
    Set<RecipeHolder<?>> brbe$getCraftable();

    /**
     * Invoker for {@code RecipeCollection.canCraft(StackedContents, int, int, RecipeBook)}.
     * Rebuilds the vanilla craftable set from ground truth (inventory + ghost recipe).
     */
    @Invoker("canCraft")
    void invokeCanCraft(StackedContents stackedContents, int gridWidth, int gridHeight, RecipeBook recipeBook);
}
