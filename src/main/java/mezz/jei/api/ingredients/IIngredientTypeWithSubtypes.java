/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.ingredients;

import mezz.jei.api.ingredients.IIngredientType;

public interface IIngredientTypeWithSubtypes<B, I>
extends IIngredientType<I> {
    @Override
    public Class<? extends I> getIngredientClass();

    public Class<? extends B> getIngredientBaseClass();

    public B getBase(I var1);

    default public I getDefaultIngredient(B base) {
        throw new UnsupportedOperationException();
    }
}

