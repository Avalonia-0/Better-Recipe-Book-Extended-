package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Collects the {@link ItemStack}s that a plugin adds to a slot into a flat
 * list, resolving every vanilla ingredient form (ItemStack / ItemLike /
 * ItemStackTemplate / Ingredient / SlotDisplay) through vanilla APIs.  Fluids
 * and custom {@link IIngredientType}s are dropped — the consumer only queries
 * item recipes.  This is the data half of JEI's {@code DisplayIngredientAcceptor},
 * without the {@code ITypedIngredient} normalization.
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

    public void addTemplate(ItemStackTemplate template) {
        if (template != null) {
            addStack(template.create());
        }
    }

    public void addIngredient(Ingredient ingredient) {
        if (ingredient == null) {
            return;
        }
        ingredient.items().forEach(holder -> addStack(new ItemStack(holder.value())));
    }

    public void addSlotDisplay(SlotDisplay display) {
        if (display == null) {
            return;
        }
        // Level-backed context first (vanilla API, no BRBE cache): fall back to
        // the null context, which resolves holder-less displays.
        ContextMap context = context();
        if (context != null) {
            for (ItemStack stack : display.resolveForStacks(context)) {
                addStack(stack);
            }
            if (!stacks.isEmpty()) {
                return;
            }
        }
        try {
            for (ItemStack stack : display.resolveForStacks(null)) {
                addStack(stack);
            }
        } catch (Exception | LinkageError ignored) {
            // unresolvable display — no stacks
        }
    }

    /** The current level's slot-display context, or null when no level is up. */
    private static ContextMap context() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : SlotDisplayContext.fromLevel(mc.level);
        } catch (Exception | LinkageError e) {
            return null;
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
        } else if (value instanceof ItemStackTemplate template) {
            addTemplate(template);
        } else if (value instanceof Ingredient ingredient) {
            addIngredient(ingredient);
        } else if (value instanceof SlotDisplay display) {
            addSlotDisplay(display);
        } else if (value instanceof ITypedIngredient<?> typed) {
            addTypedIngredient(typed);
        }
        // anything else is dropped
    }
}
