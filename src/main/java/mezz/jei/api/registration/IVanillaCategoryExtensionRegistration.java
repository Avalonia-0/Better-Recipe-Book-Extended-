/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.registration;

import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.IExtendableCraftingRecipeCategory;
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.IExtendableSmithingRecipeCategory;

public interface IVanillaCategoryExtensionRegistration {
    public IJeiHelpers getJeiHelpers();

    public IExtendableCraftingRecipeCategory getCraftingCategory();

    public IExtendableSmithingRecipeCategory getSmithingCategory();
}

