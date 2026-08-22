/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.recipe.advanced;

import java.util.List;
import mezz.jei.api.ingredients.ITypedIngredient;

public interface ISimpleRecipeManagerPlugin<T> {
    public boolean isHandledInput(ITypedIngredient<?> var1);

    public boolean isHandledOutput(ITypedIngredient<?> var1);

    public List<T> getRecipesForInput(ITypedIngredient<?> var1);

    public List<T> getRecipesForOutput(ITypedIngredient<?> var1);

    public List<T> getAllRecipes();
}

