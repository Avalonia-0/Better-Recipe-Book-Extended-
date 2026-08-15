package com.alonie.brbe.jei.plugins.stub;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.library.gui.elements.DrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal {@link IGuiHelper} that hands plugins non-null empty drawables so
 * their category constructors don't NPE.  BRBE ignores all of this — it only
 * reads the recipe data the category exposes through {@code setRecipe}.
 *
 * <p>Unlike the pure {@code EmptyDrawable} past, {@link #drawableBuilder} now
 * records each drawable's texture region so the category collector can
 * attribute a category's background (the first full-size, origin-anchored
 * drawable).  The records accumulate here and are drained per category via
 * {@link #drainBackgrounds()}.</p>
 */
public final class GuiHelperStub implements IGuiHelper {

    public static final GuiHelperStub INSTANCE = new GuiHelperStub();

    /** Drawable builders recorded since the last {@link #drainBackgrounds()}. */
    private final List<RecordedDrawableBuilder> recent = new ArrayList<>();

    private GuiHelperStub() {}

    @Override
    public IDrawableBuilder drawableBuilder(Identifier id, int u, int v, int width, int height) {
        // Record the texture region so the category collector can attribute the
        // category's background, then hand back a real builder whose build()/buildAnimated()
        // produce drawables that actually render (animation included).
        recent.add(new RecordedDrawableBuilder(id, u, v, width, height));
        return new DrawableBuilder(id, u, v, width, height);
    }

    /** Drain and return the drawable backgrounds recorded since the last drain,
     *  in call order.  The category collector calls this once per category so
     *  each background can be attributed to the category that declared it. */
    public List<RecipeViewerEngine.RecipeBackground> drainBackgrounds() {
        List<RecipeViewerEngine.RecipeBackground> out = new ArrayList<>(recent.size());
        for (RecordedDrawableBuilder builder : recent) {
            out.add(builder.toBackground());
        }
        recent.clear();
        return out;
    }

    @Override
    @SuppressWarnings("deprecation")
    public IDrawableStatic createDrawableSprite(TextureAtlas textureAtlas, Identifier spriteId) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IScalableDrawable createScalableDrawableSprite(TextureAtlas textureAtlas, Identifier spriteId) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableAnimated createAnimatedDrawable(IDrawableStatic drawable, int ticksPerCycle, IDrawableAnimated.StartDirection startDirection, boolean inverted) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableAnimated createAnimatedDrawable(IDrawableStatic drawable, ITickTimer tickTimer, IDrawableAnimated.StartDirection startDirection) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic getSlotDrawable() {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic getOutputSlot() {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic getRecipeArrow() {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic getRecipeArrowFilled() {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableAnimated createAnimatedRecipeArrow(int ticksPerCycle) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic getRecipePlusSign() {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic getRecipeFlameFilled() {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic getRecipeFlameEmpty() {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableAnimated createAnimatedRecipeFlame(int ticksPerCycle) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic createBlankDrawable(int width, int height) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public <V> IDrawable createDrawableIngredient(IIngredientType<V> type, V ingredient) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public <V> IDrawable createDrawableIngredient(ITypedIngredient<V> ingredient) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public ICraftingGridHelper createCraftingGridHelper() {
        return null;
    }

    @Override
    public IScrollBoxWidget createScrollBoxWidget(int width, int height, int xPos, int yPos) {
        return null;
    }

    @Override
    public ITickTimer createTickTimer(int ticksPerCycle, int maxValue, boolean countDown) {
        return null;
    }
}
