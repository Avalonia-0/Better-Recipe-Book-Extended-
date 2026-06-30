package com.alonie.brbe.render;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericGhostRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Thin rendering wrapper for ghost recipe overlays.
 * All actual rendering logic remains in {@link GenericGhostRecipe}.
 */
public final class GhostRecipeRenderer {

    /** Render the ghost recipe overlay. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void render(GenericGhostRecipe ghostRecipe, GuiGraphics gui,
                       Minecraft minecraft, int x, int y, boolean wide,
                       float partialTick, BRBBookCategories.Category category) {
        if (ghostRecipe == null) return;
        ghostRecipe.render(gui, minecraft, x, y, wide, partialTick, category);
    }

    /** Render the ghost recipe tooltip. */
    @SuppressWarnings("rawtypes")
    public void renderTooltip(GenericGhostRecipe ghostRecipe, GuiGraphics gui,
                              int x, int y, int mouseX, int mouseY) {
        if (ghostRecipe == null) return;
        ghostRecipe.drawTooltip(gui, x, y, mouseX, mouseY);
    }
}
