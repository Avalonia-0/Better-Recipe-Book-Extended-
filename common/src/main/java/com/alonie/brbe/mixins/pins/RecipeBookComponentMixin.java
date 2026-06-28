package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.interfaces.IPinningComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * {@link IPinningComponent} contract for the vanilla
 * {@link RecipeBookComponent}.  Pin sorting for the vanilla book is now
 * handled by {@code pipeline/RecipeBookComponentMixin}.
 *
 * <p>This mixin still implements {@link IPinningComponent} so the generic
 * recipe book components (brewing/smithing) can use the default
 * {@code brbe$sortByPinsInPlace} method via the shared interface.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements IPinningComponent<PinnableRecipeCollection> {
}
