package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;

import java.util.List;

/**
 * An empty {@link IRecipeSlotsView}, handed to a plugin's {@code category.draw}.
 * The categories BRBE supports (Farmer's Delight, bclib) don't read the slot
 * view — they draw their own background and animated icons, and BRBE paints the
 * slot items separately from the extracted {@code RecipeLayout}.
 */
public final class EmptySlotsView implements IRecipeSlotsView {

    public static final EmptySlotsView INSTANCE = new EmptySlotsView();

    private EmptySlotsView() {}

    @Override
    public List<IRecipeSlotView> getSlotViews() {
        return List.of();
    }
}
