/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.registration;

import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.api.runtime.IScreenHelper;

public interface IRuntimeRegistration {
    public void setIngredientListOverlay(IIngredientListOverlay var1);

    public void setBookmarkOverlay(IBookmarkOverlay var1);

    public void setRecipesGui(IRecipesGui var1);

    public void setIngredientFilter(IIngredientFilter var1);

    public IRecipeManager getRecipeManager();

    public IJeiHelpers getJeiHelpers();

    public IIngredientManager getIngredientManager();

    public IScreenHelper getScreenHelper();

    public IRecipeTransferManager getRecipeTransferManager();

    public IEditModeConfig getEditModeConfig();
}

