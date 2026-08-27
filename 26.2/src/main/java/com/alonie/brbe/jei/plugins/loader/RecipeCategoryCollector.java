package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.jei.plugins.stub.GuiHelperStub;
import com.alonie.brbe.jei.plugins.stub.JeiHelpersStub;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
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
    private final Map<IRecipeCategory<?>, RecipeViewerEngine.RecipeBackground> backgrounds = new HashMap<>();

    public List<IRecipeCategory<?>> categories() {
        return categories;
    }

    /** The background attributed to each category (a category without a
     *  background drawable is absent). */
    public Map<IRecipeCategory<?>, RecipeViewerEngine.RecipeBackground> backgrounds() {
        return backgrounds;
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
                // A category's background is its origin-anchored, full-size
                // drawable (createDrawable(id, 0, 0, width, height)).  Drain the
                // drawables recorded while this category's constructor ran and
                // pick the one whose size matches the category.
                RecipeViewerEngine.RecipeBackground background =
                        findBackground(GuiHelperStub.INSTANCE.drainBackgrounds(), category);
                if (background != null) {
                    backgrounds.put(category, background);
                }
            }
        }
    }

    private static RecipeViewerEngine.RecipeBackground findBackground(
            List<RecipeViewerEngine.RecipeBackground> records, IRecipeCategory<?> category) {
        int width = category.getWidth();
        int height = category.getHeight();
        for (RecipeViewerEngine.RecipeBackground record : records) {
            if (record.u() == 0 && record.v() == 0
                    && record.width() == width && record.height() == height) {
                return record;
            }
        }
        return null;
    }
}
