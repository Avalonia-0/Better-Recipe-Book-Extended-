package com.alonie.brbe.jei.plugins.stub;

import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import net.minecraft.resources.Identifier;

/**
 * An {@link IDrawableBuilder} that records the drawable's texture region so the
 * category collector can identify the background it belongs to.  It still
 * builds a {@link RecordedDrawable} (a non-null, do-nothing drawable) so plugin
 * category constructors never NPE.
 */
public final class RecordedDrawableBuilder implements IDrawableBuilder {

    private final int width;
    private final int height;

    public RecordedDrawableBuilder(int u, int v, int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public IDrawableBuilder setTextureSize(int width, int height) {
        return this;
    }

    @Override
    public IDrawableBuilder addPadding(int paddingTop, int paddingBottom, int paddingLeft, int paddingRight) {
        return this;
    }

    @Override
    public IDrawableBuilder trim(int trimTop, int trimBottom, int trimLeft, int trimRight) {
        return this;
    }

    @Override
    public IDrawableStatic build() {
        return new RecordedDrawable(width, height);
    }

    @Override
    public IDrawableAnimated buildAnimated(int ticksPerCycle, IDrawableAnimated.StartDirection startDirection, boolean inverted) {
        return new RecordedDrawable(width, height);
    }

    @Override
    public IDrawableAnimated buildAnimated(ITickTimer tickTimer, IDrawableAnimated.StartDirection startDirection) {
        return new RecordedDrawable(width, height);
    }

}
