package com.alonie.brbe.jei.plugins.stub;

import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;

/** An {@link IDrawableBuilder} that always builds {@link EmptyDrawable}. */
public final class EmptyDrawableBuilder implements IDrawableBuilder {

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
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableAnimated buildAnimated(int ticksPerCycle, IDrawableAnimated.StartDirection startDirection, boolean inverted) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableAnimated buildAnimated(ITickTimer tickTimer, IDrawableAnimated.StartDirection startDirection) {
        return EmptyDrawable.INSTANCE;
    }
}
