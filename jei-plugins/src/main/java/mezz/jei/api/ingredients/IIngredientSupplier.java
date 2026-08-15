// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.ingredients;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.List;

/**
 * A supplier for ingredients.
 * Useful for getting ingredients out of a recipe.
 *
 * Get an instance from {@link IRecipeManager#getRecipeIngredients}
 *
 * @since 19.9.0
 */
public interface IIngredientSupplier {
	/**
	 * Get all the ingredients for the given role.
	 * @since 19.9.0
	 */
	List<ITypedIngredient<?>> getIngredients(RecipeIngredientRole role);
}
