// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.api.gui.widgets;

import mezz.jei.api.gui.placement.IPlaceable;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * A scrolling area for ingredients with a scrollbar.
 * Modeled after the vanilla creative menu.
 *
 * Create one with {@link IRecipeExtrasBuilder#addScrollGridWidget}.
 * @since 19.19.3
 */
public interface IScrollGridWidget extends ISlottedRecipeWidget, IPlaceable<IScrollGridWidget> {
	/**
	 * Get the position and size of this widget, relative to its parent element.
	 *
	 * @since 19.19.3
	 */
	ScreenRectangle getScreenRectangle();
}
