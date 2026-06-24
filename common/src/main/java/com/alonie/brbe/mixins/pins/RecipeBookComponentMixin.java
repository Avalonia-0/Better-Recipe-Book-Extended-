package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.interfaces.IPinningComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Registers {@link IPinningComponent} on the vanilla {@link RecipeBookComponent}.
 * Pin sorting is now handled by {@code CollectionPipeline.applyPins()} in the
 * {@code pipeline/RecipeBookComponentMixin} redirect.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements IPinningComponent<PinnableRecipeCollection> {
}
