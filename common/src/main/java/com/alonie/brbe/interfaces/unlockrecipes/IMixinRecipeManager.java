package com.alonie.brbe.interfaces.unlockrecipes;

import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.Set;

/**
 * Access interface for RecipeManagerMixin
 */
public interface IMixinRecipeManager {

    Set<RecipeDisplayId> betterRecipeBook$getServerUnlockedRecipes();

}
