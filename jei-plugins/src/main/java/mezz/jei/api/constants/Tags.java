// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.constants;

import net.minecraft.resources.Identifier;

/**
 * Shared constants related to tags.
 * @since 19.3.0
 */
public class Tags {
	/**
	 * Ingredients marked with this tag will be hidden from JEI.
	 * @since 19.3.0
	 */
	public static final Identifier HIDDEN_FROM_RECIPE_VIEWERS = Identifier.fromNamespaceAndPath("c", "hidden_from_recipe_viewers");
}
