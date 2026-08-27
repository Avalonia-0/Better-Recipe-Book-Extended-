package com.alonie.brbe.jei.plugins.stub;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A do-nothing drawable that carries its texture region (see
 * {@link RecordedDrawableBuilder}), so {@code GuiHelperStub} can report the
 * background a plugin declared.  Rendering is a no-op — BRBE never draws these.
 */
public final class RecordedDrawable implements IDrawableStatic, IDrawableAnimated, IScalableDrawable {

    private final RecipeViewerEngine.RecipeBackground background;

    public RecordedDrawable(RecipeViewerEngine.RecipeBackground background) {
        this.background = background;
    }

    @Override
    public int getWidth() {
        return background.width();
    }

    @Override
    public int getHeight() {
        return background.height();
    }

    @Override
    public void draw(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset) {
    }

    @Override
    public void draw(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset,
                     int maskTop, int maskBottom, int maskLeft, int maskRight) {
    }

    @Override
    public void draw(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height) {
    }
}
