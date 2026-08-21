package com.alonie.brbe.jei.plugins.stub;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
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

    private final Identifier id;
    private final int u;
    private final int v;
    private final int width;
    private final int height;
    private int textureWidth = 256;
    private int textureHeight = 256;

    public RecordedDrawableBuilder(Identifier id, int u, int v, int width, int height) {
        this.id = id;
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
    }

    @Override
    public IDrawableBuilder setTextureSize(int width, int height) {
        this.textureWidth = width;
        this.textureHeight = height;
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
        return new RecordedDrawable(toBackground());
    }

    @Override
    public IDrawableAnimated buildAnimated(int ticksPerCycle, IDrawableAnimated.StartDirection startDirection, boolean inverted) {
        return new RecordedDrawable(toBackground());
    }

    @Override
    public IDrawableAnimated buildAnimated(ITickTimer tickTimer, IDrawableAnimated.StartDirection startDirection) {
        return new RecordedDrawable(toBackground());
    }

    /** The recorded background texture region. */
    RecipeViewerEngine.RecipeBackground toBackground() {
        return new RecipeViewerEngine.RecipeBackground(id, u, v, width, height, textureWidth, textureHeight);
    }
}
