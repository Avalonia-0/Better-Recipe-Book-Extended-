/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.runtime;

import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMappings;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.api.runtime.config.IJeiConfigManager;

public interface IJeiRuntime {
    public IRecipeManager getRecipeManager();

    public IRecipesGui getRecipesGui();

    public IIngredientFilter getIngredientFilter();

    public IIngredientListOverlay getIngredientListOverlay();

    public IBookmarkOverlay getBookmarkOverlay();

    public IJeiHelpers getJeiHelpers();

    public IIngredientManager getIngredientManager();

    public IJeiKeyMappings getKeyMappings();

    public IScreenHelper getScreenHelper();

    public IRecipeTransferManager getRecipeTransferManager();

    public IEditModeConfig getEditModeConfig();

    public IJeiConfigManager getConfigManager();
}

