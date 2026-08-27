package com.alonie.brbe.jei.plugins.stub;

import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal {@link IGuiHelper} that hands plugins non-null empty drawables so
 * their category constructors don't NPE.  BRBE ignores all of this — it only
 * reads the recipe data the category exposes through {@code setRecipe}.
 */
public final class GuiHelperStub implements IGuiHelper {

    public static final GuiHelperStub INSTANCE = new GuiHelperStub();

    private GuiHelperStub() {}

    @Override
    public IDrawableBuilder drawableBuilder(ResourceLocation resourceLocation, int u, int v, int width, int height) {
        return new RecordedDrawableBuilder(u, v, width, height);
    }

    @Override
    public IDrawableStatic createDrawableSprite(TextureAtlas textureAtlas, ResourceLocation spriteId) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IDrawableStatic createDrawableSprite(TextureAtlas textureAtlas, ResourceLocation spriteId, int width, int height) {
        return EmptyDrawable.INSTANCE;
    }

    @Override
    public IScalableDrawable createScalableDrawableSprite(TextureAtlas textureAtlas, ResourceLocation spriteId) {
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
    public IScrollBoxWidget createScrollBoxWidget(IDrawable contents, int visibleHeight, int xPos, int yPos) {
        return null;
    }

    @Override
    public IScrollBoxWidget createScrollBoxWidget(int width, int height, int xPos, int yPos) {
        return null;
    }

    @Override
    public int getScrollBoxScrollbarExtraWidth() {
        return 0;
    }

    @Override
    public IRecipeWidget createWidgetFromDrawable(IDrawable drawable, int xPos, int yPos) {
        return null;
    }

    @Override
    public mezz.jei.api.gui.widgets.IScrollGridWidgetFactory<?> createScrollGridFactory(int columns, int visibleRows) {
        return null;
    }

    @Override
    public ITickTimer createTickTimer(int ticksPerCycle, int maxValue, boolean countDown) {
        return null;
    }
}
