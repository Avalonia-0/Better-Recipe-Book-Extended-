package com.alonie.brbe.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Renders a synthetic (mod) recipe's full JEI-style UI — its category background,
 * animated drawables and slot items — fitted to a given content area.
 *
 * <p>Defined in common so the front-end mixins can call it without a compile
 * dependency on the JEI API ({@code IRecipeCategory}/{@code IRecipeSlotsView}
 * only exist in the companion mod's fork).  The companion mod
 * (zzzbrbe-jei-plugins) provides the real implementation and registers it via
 * {@link SyntheticRecipeRenderers}.</p>
 *
 * <p>The {@code (x, y, w, h)} rect is the recipe's <b>content area</b> (already
 * fitted and scaled by the caller's shared {@code PopupGeometry}): the renderer
 * wraps it in its container panel and scales the JEI drawable to fill it, so
 * the panel, hit volume and JEI exclusion all agree.</p>
 */
public interface SyntheticRecipeRenderer {

    SyntheticRecipeRenderer NONE = (id, gui, x, y, w, h) -> false;

    /**
     * Whether this renderer can actually paint {@code id} right now.  The
     * shared {@code PopupGeometry} uses this to pick the popup's coordinate
     * model: only a renderer that will really paint the adapted JEI UI gets
     * the adapted geometry — otherwise the geometry must match the vanilla
     * fallback, or the hit volume would exceed the rendered popup.  The
     * default assumes the renderer works for every recipe.
     */
    default boolean canRender(RecipeDisplayId id) {
        return true;
    }

    /**
     * Render the recipe's full UI into the content area {@code (x, y, w, h)}.
     *
     * @return true if it rendered (the caller should skip its own backdrop and
     *         item rendering); false to fall back to the caller's static rendering.
     */
    boolean render(RecipeDisplayId id, GuiGraphicsExtractor gui, int x, int y, int w, int h);
}
