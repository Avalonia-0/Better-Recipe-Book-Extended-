package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.jei.plugins.stub.JeiHelpersStub;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link IRecipeRegistration} implementation that records every
 *  {@code recipeType -> recipes} mapping reported by the loaded JEI plugins,
 *  without registering them into a JEI runtime. */
public final class RecipeCollector implements IRecipeRegistration {

    private final Map<IRecipeType<?>, List<Object>> recipes = new LinkedHashMap<>();

    public Map<IRecipeType<?>, List<Object>> recipes() {
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
    public <T> void addRecipes(IRecipeType<T> recipeType, List<T> recipes) {
        if (recipeType == null || recipes == null) return;
        this.recipes.computeIfAbsent(recipeType, k -> new ArrayList<>()).addAll(recipes);
    }

    @Override
    public <T> void addIngredientInfo(T ingredient, IIngredientType<T> ingredientType, Component... descriptionComponents) {
        // ingredient info pages are not recipes; ignored
    }

    @Override
    public <T> void addIngredientInfo(List<T> ingredients, IIngredientType<T> ingredientType, Component... descriptionComponents) {
        // ingredient info pages are not recipes; ignored
    }

    @Override
    public net.minecraft.util.context.ContextMap getContextMap() {
        return null;
    }
}
