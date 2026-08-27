package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.gui.drawable.TilingDirection;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;

/**
 * Data-only {@link IRecipeSlotBuilder}: records the ingredient items added for
 * a slot (visible and data-only), its lookup role and (when the category calls
 * {@link #setPosition}) its layout position.  Every rendering/tooltip concern
 * is a no-op — BRBE only extracts the data.
 */
public final class DataOnlySlotBuilder implements IRecipeSlotBuilder {

    private final RecipeIngredientRole role;
    private final ItemStackCollector collector = new ItemStackCollector();
    private int x;
    private int y;

    public DataOnlySlotBuilder(RecipeIngredientRole role) {
        this.role = role;
    }

    public RecipeIngredientRole role() {
        return role;
    }

    public ItemStackCollector collector() {
        return collector;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    // ---- IIngredientAcceptor (data collection) ----

    @Override
    public <I> DataOnlySlotBuilder addIngredients(IIngredientType<I> ingredientType, List<I> ingredients) {
        collector.addUnsafe(ingredients);
        return this;
    }

    @Override
    public <I> DataOnlySlotBuilder addIngredient(IIngredientType<I> ingredientType, I ingredient) {
        collector.addTyped(ingredientType, ingredient);
        return this;
    }

    @Override
    public DataOnlySlotBuilder addIngredientsUnsafe(List<?> ingredients) {
        collector.addUnsafe(ingredients);
        return this;
    }

    @Override
    public DataOnlySlotBuilder addTypedIngredients(List<ITypedIngredient<?>> ingredients) {
        if (ingredients != null) {
            for (ITypedIngredient<?> ingredient : ingredients) {
                collector.addTypedIngredient(ingredient);
            }
        }
        return this;
    }

    @Override
    public DataOnlySlotBuilder addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> ingredients) {
        if (ingredients != null) {
            for (Optional<ITypedIngredient<?>> ingredient : ingredients) {
                ingredient.ifPresent(collector::addTypedIngredient);
            }
        }
        return this;
    }

    @Override
    public DataOnlySlotBuilder addFluidStack(Fluid fluid) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder addFluidStack(Fluid fluid, long amount) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder addFluidStack(Fluid fluid, long amount, DataComponentPatch component) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder addItemStacks(List<ItemStack> itemStacks) {
        if (itemStacks != null) {
            for (ItemStack itemStack : itemStacks) {
                collector.addStack(itemStack);
            }
        }
        return this;
    }

    @Override
    public DataOnlySlotBuilder addItemStack(ItemStack itemStack) {
        collector.addStack(itemStack);
        return this;
    }

    @Override
    public DataOnlySlotBuilder addItemLike(ItemLike itemLike) {
        collector.addItemLike(itemLike);
        return this;
    }

    @Override
    public DataOnlySlotBuilder addIngredients(Ingredient ingredient) {
        collector.addIngredient(ingredient);
        return this;
    }

    // ---- IRecipeSlotBuilder (rendering — no-op) ----

    @Override
    public DataOnlySlotBuilder addTooltipCallback(IRecipeSlotTooltipCallback tooltipCallback) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder addRichTooltipCallback(IRecipeSlotRichTooltipCallback tooltipCallback) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder setSlotName(String slotName) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder setStandardSlotBackground() {
        return this;
    }

    @Override
    public DataOnlySlotBuilder setOutputSlotBackground() {
        return this;
    }

    @Override
    public DataOnlySlotBuilder setBackground(IDrawable background, int xOffset, int yOffset) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder setOverlay(IDrawable overlay, int xOffset, int yOffset) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder setFluidRenderer(long capacity, boolean showCapacity, int width, int height) {
        return this;
    }

    @Override
    public DataOnlySlotBuilder setFluidRenderer(long capacity, boolean showCapacity, int width, int height, TilingDirection tilingDirection) {
        return this;
    }

    @Override
    public <T> DataOnlySlotBuilder setCustomRenderer(IIngredientType<T> ingredientType, IIngredientRenderer<T> ingredientRenderer) {
        return this;
    }

    // ---- IPlaceable (positioning — record the slot's layout position) ----

    @Override
    public DataOnlySlotBuilder setPosition(int xPos, int yPos) {
        this.x = xPos;
        this.y = yPos;
        return this;
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }
}
