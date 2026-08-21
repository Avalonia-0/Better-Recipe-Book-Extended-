// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.recipe.vanilla;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * There is no vanilla registry of Anvil Recipes,
 * so JEI creates these Anvil recipes to use internally.
 *
 * Create your own with {@link IVanillaRecipeFactory#createAnvilRecipe}
 */
public interface IJeiAnvilRecipe {
	/**
	 * Get the inputs that go into the left slot of the Anvil.
	 *
	 * @since 9.5.0
	 */
	List<ItemStack> getLeftInputs();

	/**
	 * Get the inputs that go into the right slot of the Anvil.
	 *
	 * @since 9.5.0
	 */
	List<ItemStack> getRightInputs();

	/**
	 * Get the outputs of the Anvil recipe.
	 *
	 * @since 9.5.0
	 */
	List<ItemStack> getOutputs();

	/**
	 * Unique ID for this recipe.
	 * @since 19.1.0
	 */
	Identifier getUid();
}
