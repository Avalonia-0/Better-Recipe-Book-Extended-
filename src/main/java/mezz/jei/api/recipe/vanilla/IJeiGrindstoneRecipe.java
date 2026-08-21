// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.recipe.vanilla;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * There is no vanilla registry of Grindstone Recipes,
 * so JEI creates these Grindstone recipes to use internally.
 *
 * Create your own with {@link IVanillaRecipeFactory#createGrindstoneRecipe}
 * @since 23.1.0
 */
public interface IJeiGrindstoneRecipe {
	/**
	 * Get the inputs that go into the top slot of the Grindstone.
	 *
	 * @since 23.1.0
	 */
	List<ItemStack> getTopInputs();

	/**
	 * Get the inputs that go into the bottom slot of the Grindstone.
	 *
	 * @since 23.1.0
	 */
	List<ItemStack> getBottomInputs();

	/**
	 * Get the outputs of the Grindstone recipe.
	 *
	 * @since 23.1.0
	 */
	List<ItemStack> getOutputs();

	/**
	 * The minimum XP that a player can receive.
	 *
	 * @since 23.1.0
	 */
	int getMinXpReward();

	/**
	 * The maximum XP that a player can receive.
	 *
	 * @since 23.1.0
	 */
	int getMaxXpReward();

	/**
	 * Unique ID for this recipe.
	 *
	 * @since 23.1.0
	 */
	Identifier getUid();

	/**
	 * Make the output render only, to avoid displaying unnecessary crafting recipes when looking up outputs.
	 *
	 * @since 23.1.0
	 */
	boolean isOutputRenderOnly();
}
