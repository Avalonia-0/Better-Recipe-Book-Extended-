/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.api.ingredients;

import java.util.List;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

@ApiStatus.NonExtendable
public interface IIngredientSupplier {
    public @Unmodifiable List<ITypedIngredient<?>> getIngredients(RecipeIngredientRole var1);
}

