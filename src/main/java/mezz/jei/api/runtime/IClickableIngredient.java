/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.Rect2i
 */
package mezz.jei.api.runtime;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;

public interface IClickableIngredient<T> {
    public ITypedIngredient<T> getTypedIngredient();

    @Deprecated(since="21.2.0", forRemoval=true)
    default public IIngredientType<T> getIngredientType() {
        return this.getTypedIngredient().getType();
    }

    @Deprecated(since="21.2.0", forRemoval=true)
    default public T getIngredient() {
        ITypedIngredient<T> typedIngredient = this.getTypedIngredient();
        return typedIngredient.getIngredient();
    }

    public Rect2i getArea();
}

