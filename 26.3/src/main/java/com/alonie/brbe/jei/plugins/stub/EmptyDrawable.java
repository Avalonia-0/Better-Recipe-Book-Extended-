package com.alonie.brbe.jei.plugins.stub;

import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A do-nothing drawable implementing every drawable flavour, so the companion
 * mod's {@code IGuiHelper} stub can hand plugins a non-null icon/background
 * without any rendering.  BRBE never draws these — it only reads recipe data.
 */
public final class EmptyDrawable implements IDrawableStatic, IDrawableAnimated, IScalableDrawable {

    public static final EmptyDrawable INSTANCE = new EmptyDrawable();

    private EmptyDrawable() {}

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
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
