// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.gui.ingredient;

import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;

/**
 * Used to add tooltips to ingredients drawn on a recipe.
 *
 * Implement a tooltip callback and add it with
 * {@link IRecipeSlotBuilder#addRichTooltipCallback(IRecipeSlotRichTooltipCallback)}
 *
 * @since 19.8.5
 */
@FunctionalInterface
public interface IRecipeSlotRichTooltipCallback {
	/**
	 * Add to the tooltip for an ingredient.
	 *
	 * @since 19.8.5
	 */
	void onRichTooltip(IRecipeSlotView recipeSlotView, ITooltipBuilder tooltip);
}
