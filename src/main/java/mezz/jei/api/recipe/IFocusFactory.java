/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.recipe;

import java.util.Collection;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;

public interface IFocusFactory {
    public <V> IFocus<V> createFocus(RecipeIngredientRole var1, IIngredientType<V> var2, V var3);

    public <V> IFocus<V> createFocus(RecipeIngredientRole var1, ITypedIngredient<V> var2);

    public IFocusGroup createFocusGroup(Collection<? extends IFocus<?>> var1);

    public IFocusGroup getEmptyFocusGroup();
}

