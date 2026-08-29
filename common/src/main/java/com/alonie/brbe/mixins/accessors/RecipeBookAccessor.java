package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Exposes the recipe book's known recipe-id set (declared on the
 * {@code RecipeBook} superclass; mixin accessors resolve inherited fields).
 * The query engine's known-set-driven rebuild reads this authority — only
 * recipes the player has actually unlocked (plus locally injected vanilla
 * cache) are candidates, matching the 1.21.11/26.2 semantics.
 */
@Mixin(ClientRecipeBook.class)
public interface RecipeBookAccessor {
    @Accessor("known")
    Set<ResourceLocation> brbe$getKnown();
}
