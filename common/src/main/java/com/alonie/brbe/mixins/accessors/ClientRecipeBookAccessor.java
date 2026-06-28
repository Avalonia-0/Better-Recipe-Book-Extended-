package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private {@code setupCollections} method on
 * {@link ClientRecipeBook} so {@link com.alonie.brbe.util.RecipeUnlockUtil}
 * can force a full rebuild of recipe collections after populating
 * {@code knownKeys} with all recipes.
 */
@Mixin(ClientRecipeBook.class)
public interface ClientRecipeBookAccessor {
    @Invoker("setupCollections")
    void brbe$setupCollections(Iterable<RecipeHolder<?>> recipes, RegistryAccess registryAccess);
}
