/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.recipe.advanced;

import java.util.List;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.types.IRecipeType;

public interface IRecipeManagerPlugin {
    public <V> List<IRecipeType<?>> getRecipeTypes(IFocus<V> var1);

    public <T, V> List<T> getRecipes(IRecipeType<T> var1, IFocus<V> var2);

    public <T> List<T> getRecipes(IRecipeType<T> var1);
}

