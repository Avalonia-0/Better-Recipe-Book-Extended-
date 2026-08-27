package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.jei.plugins.stub.JeiHelpersStub;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link IRecipeRegistration} implementation that records every
 *  {@code recipeType -> recipes} mapping reported by the loaded JEI plugins,
 *  without registering them into a JEI runtime. */
public final class RecipeCollector implements IRecipeRegistration {

    private final Map<RecipeType<?>, List<Object>> recipes = new LinkedHashMap<>();

    public Map<RecipeType<?>, List<Object>> recipes() {
        return recipes;
    }

    @Override
    public IJeiHelpers getJeiHelpers() {
        return JeiHelpersStub.INSTANCE;
    }

    @Override
    public IIngredientManager getIngredientManager() {
        return null;
    }

    @Override
    public IVanillaRecipeFactory getVanillaRecipeFactory() {
        return null;
    }

    @Override
    public <T> void addRecipes(RecipeType<T> recipeType, List<T> recipes) {
        if (recipeType == null || recipes == null) return;
        List<Object> bucket = this.recipes.computeIfAbsent(recipeType, k -> new ArrayList<>());
        bucket.addAll(recipes);
    }

    @Override
    public <T> void addIngredientInfo(T ingredient, IIngredientType<T> ingredientType, Component... descriptionComponents) {
        // ingredient info pages are not recipes; ignored
    }

    @Override
    public <T> void addIngredientInfo(List<T> ingredients, IIngredientType<T> ingredientType, Component... descriptionComponents) {
        // ingredient info pages are not recipes; ignored
    }
}
