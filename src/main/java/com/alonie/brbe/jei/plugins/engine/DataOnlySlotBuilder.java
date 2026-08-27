package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.TilingDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;

/**
 * Data-only {@link IRecipeSlotBuilder}: records the ingredient items added for
 * its {@link RecipeIngredientRole}, and no-ops every positioning / background /
 * renderer / tooltip callback.  BRBE only needs the item contents, never the
 * layout.
 */
public final class DataOnlySlotBuilder implements IRecipeSlotBuilder {

    private final RecipeIngredientRole role;
    private final ItemStackCollector collector;
    private final ContextMap contextMap;
    private int x;
    private int y;

    public DataOnlySlotBuilder(RecipeIngredientRole role) {
        this(role, null);
    }

    public DataOnlySlotBuilder(RecipeIngredientRole role, ContextMap contextMap) {
        this.role = role;
        this.collector = new ItemStackCollector();
        this.contextMap = contextMap;
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
    public IRecipeSlotBuilder add(SlotDisplay slotDisplay) {
        collector.addSlotDisplay(slotDisplay);
        return this;
    }

    @Override
    public <I> IRecipeSlotBuilder add(IIngredientType<I> ingredientType, SlotDisplay slotDisplay) {
        collector.addSlotDisplay(slotDisplay);
        return this;
    }

    @Override
    public IRecipeSlotBuilder add(ItemStack itemStack) {
        collector.addStack(itemStack);
        return this;
    }

    @Override
    public IRecipeSlotBuilder add(ItemLike itemLike) {
        collector.addItemLike(itemLike);
        return this;
    }

    @Override
    public IRecipeSlotBuilder add(ItemStackTemplate itemStackTemplate) {
        collector.addTemplate(itemStackTemplate);
        return this;
    }

    @Override
    public IRecipeSlotBuilder add(Fluid fluid) {
        return this;
    }

    @Override
    public IRecipeSlotBuilder add(Fluid fluid, long amount) {
        return this;
    }

    @Override
    public IRecipeSlotBuilder add(Fluid fluid, long amount, DataComponentPatch component) {
        return this;
    }

    @Override
    public IRecipeSlotBuilder add(Ingredient ingredient) {
        collector.addIngredient(ingredient);
        return this;
    }

    @Override
    public <I> IRecipeSlotBuilder add(IIngredientType<I> ingredientType, Ingredient ingredient) {
        collector.addIngredient(ingredient);
        return this;
    }

    @Override
    public <I> IRecipeSlotBuilder add(ITypedIngredient<I> typedIngredient) {
        collector.addTypedIngredient(typedIngredient);
        return this;
    }

    @Override
    public <I> IRecipeSlotBuilder add(IIngredientType<I> ingredientType, I ingredient) {
        collector.addTyped(ingredientType, ingredient);
        return this;
    }

    @Override
    public <I> IRecipeSlotBuilder addIngredients(IIngredientType<I> ingredientType, List<I> ingredients) {
        collector.addUnsafe(ingredients);
        return this;
    }

    @Override
    public IRecipeSlotBuilder addIngredientsUnsafe(List<?> ingredients) {
        collector.addUnsafe(ingredients);
        return this;
    }

    @Override
    public IRecipeSlotBuilder addTypedIngredients(List<ITypedIngredient<?>> ingredients) {
        if (ingredients != null) {
            for (ITypedIngredient<?> ingredient : ingredients) {
                collector.addTypedIngredient(ingredient);
            }
        }
        return this;
    }

    @Override
    public IRecipeSlotBuilder addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> ingredients) {
        if (ingredients != null) {
            for (Optional<ITypedIngredient<?>> ingredient : ingredients) {
                ingredient.ifPresent(collector::addTypedIngredient);
            }
        }
        return this;
    }

    @Override
    public IRecipeSlotBuilder addItemStacks(List<ItemStack> itemStacks) {
        if (itemStacks != null) {
            for (ItemStack itemStack : itemStacks) {
                collector.addStack(itemStack);
            }
        }
        return this;
    }

    @Override
    public ContextMap getContextMap() {
        return contextMap;
    }

    // ---- IRecipeSlotBuilder (rendering — no-op) ----

    @Override
    public IRecipeSlotBuilder addRichTooltipCallback(IRecipeSlotRichTooltipCallback tooltipCallback) {
        return this;
    }

    @Override
    public IRecipeSlotBuilder setSlotName(String slotName) {
        return this;
    }

    @Override
    public IRecipeSlotBuilder setStandardSlotBackground() {
        return this;
    }

    @Override
    public IRecipeSlotBuilder setOutputSlotBackground() {
        return this;
    }

    @Override
    public IRecipeSlotBuilder setBackground(IDrawable background, int xOffset, int yOffset) {
        return this;
    }

    @Override
    public IRecipeSlotBuilder setOverlay(IDrawable overlay, int xOffset, int yOffset) {
        return this;
    }

    @Override
    public IRecipeSlotBuilder setFluidRenderer(long capacity, boolean showCapacity, int width, int height) {
        return this;
    }

    @Override
    public IRecipeSlotBuilder setFluidRenderer(long capacity, boolean showCapacity, int width, int height, TilingDirection tilingDirection) {
        return this;
    }

    @Override
    public <T> IRecipeSlotBuilder setCustomRenderer(IIngredientType<T> ingredientType, IIngredientRenderer<T> ingredientRenderer) {
        return this;
    }

    // ---- IPlaceable (positioning — record the slot's layout position) ----

    @Override
    public IRecipeSlotBuilder setPosition(int xPos, int yPos) {
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
