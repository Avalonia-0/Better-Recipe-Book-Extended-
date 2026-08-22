package mezz.jei.api.recipe.vanilla;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Recipes representing ingredients that can be composted in the composter.
 *
 * JEI automatically creates these recipes from {@link ComposterBlock#COMPOSTABLES}.
 *
 * @since 9.5.0
 */
@ApiStatus.NonExtendable
public interface IJeiCompostingRecipe {
	/**
	 * Get the inputs to this recipe.
	 * @since 9.5.0
	 */
		List<ItemStack> getInputs();

	/**
	 * Get the chance of this input adding a level of compost to the composter.
	 *
	 * @since 9.5.0
	 */
		float getChance();

	/**
	 * Unique ID for this recipe.
	 * @since 19.1.0
	 */
	Identifier getUid();
}
