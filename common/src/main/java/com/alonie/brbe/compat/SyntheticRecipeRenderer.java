package com.alonie.brbe.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Renders a synthetic (mod) recipe's full JEI-style UI — its category background,
 * animated drawables and slot items — fitted to a button area.
 *
 * <p>Defined in common so the front-end mixins can call it without a compile
 * dependency on the JEI API ({@code IRecipeCategory}/{@code IRecipeSlotsView}
 * only exist in the companion mod's fork).  The companion mod
 * (brbe-jei-plugins) provides the real implementation and registers it via
 * {@link SyntheticRecipeRenderers}.</p>
 */
public interface SyntheticRecipeRenderer {

    SyntheticRecipeRenderer NONE = (id, gui, x, y, w, h) -> false;

    /**
     * Render the recipe's full UI, fitted into {@code (x, y, w, h)}.
     *
     * @return true if it rendered (the caller should skip its own backdrop and
     *         item rendering); false to fall back to the caller's static rendering.
     */
    boolean render(RecipeDisplayId id, GuiGraphicsExtractor gui, int x, int y, int w, int h);
}
