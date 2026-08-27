package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.jei.plugins.stub.GuiHelperStub;
import com.alonie.brbe.jei.plugins.stub.JeiHelpersStub;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@link IRecipeCategoryRegistration} implementation that records every
 *  {@link IRecipeCategory} reported by the loaded JEI plugins, so BRBE can
 *  derive dynamic viewer categories from them.  It also attributes each
 *  category's background texture (drained from {@link GuiHelperStub}). */
public final class RecipeCategoryCollector implements IRecipeCategoryRegistration {

    private final List<IRecipeCategory<?>> categories = new ArrayList<>();

    public List<IRecipeCategory<?>> categories() {
        return categories;
    }

    @Override
    public IJeiHelpers getJeiHelpers() {
        return JeiHelpersStub.INSTANCE;
    }

    @Override
    public void addRecipeCategories(IRecipeCategory<?>... recipeCategories) {
        if (recipeCategories == null) return;
        for (IRecipeCategory<?> category : recipeCategories) {
            if (category != null) {
                categories.add(category);
            }
        }
    }
}
