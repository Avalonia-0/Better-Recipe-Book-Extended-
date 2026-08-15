// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.recipe.vanilla;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Fueling recipes represent items that can be used as fuel in the Furnace, Smoker, Blast Furnace, etc.
 *
 * JEI automatically creates a fueling recipe for anything that has a burn time.
 *
 * @since 9.5.0
 */
public interface IJeiFuelingRecipe {
	/**
	 * @return the inputs that act as a fuel
	 */
	List<ItemStack> getInputs();

	/**
	 * @return the fuel's burn time in ticks. Always greater than 0.
	 */
	int getBurnTime();
}
