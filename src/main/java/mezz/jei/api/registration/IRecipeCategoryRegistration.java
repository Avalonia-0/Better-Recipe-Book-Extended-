/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.registration;

import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.category.IRecipeCategory;

public interface IRecipeCategoryRegistration {
    public IJeiHelpers getJeiHelpers();

    public void addRecipeCategories(IRecipeCategory<?> ... var1);
}

