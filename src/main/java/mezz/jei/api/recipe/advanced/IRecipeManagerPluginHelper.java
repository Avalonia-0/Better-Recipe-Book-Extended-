/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.recipe.advanced;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.types.IRecipeType;

public interface IRecipeManagerPluginHelper {
    public boolean isCraftingStation(IRecipeType<?> var1, IFocus<?> var2);

    @Deprecated(forRemoval=true, since="20.0.0")
    default public boolean isRecipeCatalyst(IRecipeType<?> recipeType, IFocus<?> focus) {
        return this.isCraftingStation(recipeType, focus);
    }
}

