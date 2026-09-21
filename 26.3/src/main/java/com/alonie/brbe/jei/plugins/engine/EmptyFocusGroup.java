package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.List;
import java.util.stream.Stream;

/**
 * The empty {@link IFocusGroup} handed to a plugin's {@code setRecipe} during
 * indexing — BRBE indexes recipes unfocused, then applies the focus at query
 * time through its own reverse index.
 */
public final class EmptyFocusGroup implements IFocusGroup {

    public static final EmptyFocusGroup INSTANCE = new EmptyFocusGroup();

    private EmptyFocusGroup() {}

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public List<IFocus<?>> getAllFocuses() {
        return List.of();
    }

    @Override
    public Stream<IFocus<?>> getFocuses(RecipeIngredientRole role) {
        return Stream.empty();
    }

    @Override
    public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType) {
        return Stream.empty();
    }

    @Override
    public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType, RecipeIngredientRole role) {
        return Stream.empty();
    }
}
