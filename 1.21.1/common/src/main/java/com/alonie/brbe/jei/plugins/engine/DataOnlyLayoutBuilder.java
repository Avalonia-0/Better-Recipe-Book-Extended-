package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Data-only {@link IRecipeLayoutBuilder}: runs a plugin's
 * {@code setRecipe(builder, recipe, focuses)} and captures every declared slot
 * (visible and invisible) as {@link SlotData}, including each slot's layout
 * position.  Layout / shapeless icon / focus-link concerns are no-ops.
 */
public final class DataOnlyLayoutBuilder implements IRecipeLayoutBuilder {

    private final int width;
    private final int height;
    private final List<DataOnlySlotBuilder> slots = new ArrayList<>();
    private final List<DataOnlyIngredientAcceptor> invisible = new ArrayList<>();

    public DataOnlyLayoutBuilder(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public IRecipeSlotBuilder addSlot(RecipeIngredientRole role) {
        DataOnlySlotBuilder slot = new DataOnlySlotBuilder(role);
        slots.add(slot);
        return slot;
    }

    @Override
    public IIngredientAcceptor<?> addInvisibleIngredients(RecipeIngredientRole recipeIngredientRole) {
        DataOnlyIngredientAcceptor acceptor =
                new DataOnlyIngredientAcceptor(recipeIngredientRole, new ItemStackCollector());
        invisible.add(acceptor);
        return acceptor;
    }

    @Override
    @SuppressWarnings("removal")
    public mezz.jei.api.gui.builder.IRecipeSlotBuilder addSlotToWidget(
            RecipeIngredientRole role,
            mezz.jei.api.gui.widgets.ISlottedWidgetFactory<?> widgetFactory) {
        return addSlot(role);
    }

    @Override
    public void moveRecipeTransferButton(int posX, int posY) {
    }

    @Override
    public void setShapeless() {
    }

    @Override
    public void setShapeless(int posX, int posY) {
    }

    @Override
    public void createFocusLink(IIngredientAcceptor<?>... slots) {
    }

    /** Extract the collected slot data (visible + invisible) after setRecipe. */
    public List<SlotData> slotData() {
        List<SlotData> out = new ArrayList<>();
        for (DataOnlySlotBuilder slot : slots) {
            out.add(new SlotData(slot.role(), slot.x(), slot.y(), slot.collector().stacks()));
        }
        for (DataOnlyIngredientAcceptor acceptor : invisible) {
            out.add(new SlotData(acceptor.role(), -1, -1, acceptor.collector().stacks()));
        }
        return out;
    }
}
