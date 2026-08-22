/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 */
package mezz.jei.api.recipe;

import java.util.Optional;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface IFocus<V> {
    public ITypedIngredient<V> getTypedValue();

    public RecipeIngredientRole getRole();

    public <T> Optional<IFocus<T>> checkedCast(IIngredientType<T> var1);
}

