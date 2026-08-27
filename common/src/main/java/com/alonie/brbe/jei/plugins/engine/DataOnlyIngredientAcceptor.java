package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;

/**
 * A bare {@link IIngredientAcceptor} used for
 * {@code IRecipeLayoutBuilder#addInvisibleIngredients}.  Collects item stacks
 * only; every other ingredient form is dropped.
 */
public final class DataOnlyIngredientAcceptor implements IIngredientAcceptor<DataOnlyIngredientAcceptor> {

    private final RecipeIngredientRole role;
    private final ItemStackCollector collector;

    public DataOnlyIngredientAcceptor(RecipeIngredientRole role, ItemStackCollector collector) {
        this.role = role;
        this.collector = collector;
    }

    public RecipeIngredientRole role() {
        return role;
    }

    public ItemStackCollector collector() {
        return collector;
    }

    @Override
    public <I> DataOnlyIngredientAcceptor addIngredients(IIngredientType<I> ingredientType, List<I> ingredients) {
        collector.addUnsafe(ingredients);
        return this;
    }

    @Override
    public <I> DataOnlyIngredientAcceptor addIngredient(IIngredientType<I> ingredientType, I ingredient) {
        collector.addTyped(ingredientType, ingredient);
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addIngredientsUnsafe(List<?> ingredients) {
        collector.addUnsafe(ingredients);
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addTypedIngredients(List<ITypedIngredient<?>> ingredients) {
        if (ingredients != null) {
            for (ITypedIngredient<?> ingredient : ingredients) {
                collector.addTypedIngredient(ingredient);
            }
        }
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> ingredients) {
        if (ingredients != null) {
            for (Optional<ITypedIngredient<?>> ingredient : ingredients) {
                ingredient.ifPresent(collector::addTypedIngredient);
            }
        }
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addFluidStack(Fluid fluid) {
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addFluidStack(Fluid fluid, long amount) {
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addFluidStack(Fluid fluid, long amount, DataComponentPatch component) {
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addItemStacks(List<ItemStack> itemStacks) {
        if (itemStacks != null) {
            for (ItemStack itemStack : itemStacks) {
                collector.addStack(itemStack);
            }
        }
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addItemStack(ItemStack itemStack) {
        collector.addStack(itemStack);
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addItemLike(ItemLike itemLike) {
        collector.addItemLike(itemLike);
        return this;
    }

    @Override
    public DataOnlyIngredientAcceptor addIngredients(Ingredient ingredient) {
        collector.addIngredient(ingredient);
        return this;
    }
}
