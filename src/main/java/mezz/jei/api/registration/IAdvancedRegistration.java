/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.registration;

import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.advanced.IRecipeManagerPluginHelper;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiFeatures;

public interface IAdvancedRegistration {
    public IJeiHelpers getJeiHelpers();

    public IRecipeManagerPluginHelper getRecipeManagerPluginHelper();

    public void addRecipeManagerPlugin(IRecipeManagerPlugin var1);

    public <T> void addSimpleRecipeManagerPlugin(IRecipeType<T> var1, ISimpleRecipeManagerPlugin<T> var2);

    @Deprecated(forRemoval=true, since="20.0.0")
    default public <T> void addTypedRecipeManagerPlugin(IRecipeType<T> recipeType, ISimpleRecipeManagerPlugin<T> recipeManagerPlugin) {
        this.addSimpleRecipeManagerPlugin(recipeType, recipeManagerPlugin);
    }

    public <T> void addRecipeCategoryDecorator(IRecipeType<T> var1, IRecipeCategoryDecorator<T> var2);

    public void addRecipeButtonFactory(IRecipeButtonControllerFactory var1);

    public IJeiFeatures getJeiFeatures();
}

