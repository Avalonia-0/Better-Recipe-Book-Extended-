/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 */
package mezz.jei.api.runtime;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface IIngredientVisibility {
    public <V> boolean isIngredientVisible(IIngredientType<V> var1, V var2);

    public <V> boolean isIngredientVisible(ITypedIngredient<V> var1);

    public void registerListener(IListener var1);

    public static interface IListener {
        public <V> void onIngredientVisibilityChanged(ITypedIngredient<V> var1, boolean var2);
    }
}

