package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collects the {@link ItemStack}s that a plugin adds to a slot into a flat
 * list, resolving every vanilla ingredient form (ItemStack / ItemLike /
 * Ingredient) through vanilla APIs.  Fluids and custom {@link IIngredientType}s
 * are dropped — BRBE only queries item recipes.  This is the data half of JEI's
 * {@code DisplayIngredientAcceptor}, without the {@code ITypedIngredient}
 * normalization.  (1.21.1 无 SlotDisplay——1.21.5+ 特性。)
 */
public final class ItemStackCollector {

    private final List<ItemStack> stacks = new ArrayList<>();

    public List<ItemStack> stacks() {
        return stacks;
    }

    public void addStack(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stacks.add(stack);
        }
    }

    public void addItemLike(ItemLike itemLike) {
        if (itemLike != null) {
            addStack(new ItemStack(itemLike));
        }
    }

    public void addIngredient(Ingredient ingredient) {
        if (ingredient == null) {
            return;
        }
        for (ItemStack stack : ingredient.getItems()) {
            addStack(stack);
        }
    }

    public void addTyped(IIngredientType<?> type, Object value) {
        if (type == VanillaTypes.ITEM_STACK && value instanceof ItemStack stack) {
            addStack(stack);
        }
        // fluid / custom ingredient types are dropped
    }

    public void addTypedIngredient(ITypedIngredient<?> typed) {
        if (typed != null) {
            typed.getItemStack().ifPresent(this::addStack);
        }
    }

    /** Dispatch a mixed list (from {@code addIngredientsUnsafe} / {@code addTypedIngredients}). */
    public void addUnsafe(List<?> ingredients) {
        if (ingredients == null) {
            return;
        }
        for (Object element : ingredients) {
            if (element instanceof Optional<?> optional) {
                optional.ifPresent(this::addSingleUnsafe);
            } else {
                addSingleUnsafe(element);
            }
        }
    }

    private void addSingleUnsafe(Object value) {
        if (value instanceof ItemStack stack) {
            addStack(stack);
        } else if (value instanceof ItemLike itemLike) {
            addItemLike(itemLike);
        } else if (value instanceof Ingredient ingredient) {
            addIngredient(ingredient);
        } else if (value instanceof ITypedIngredient<?> typed) {
            addTypedIngredient(typed);
        }
        // anything else is dropped
    }
}
