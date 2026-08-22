/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.runtime;

import java.util.List;
import java.util.Optional;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.jspecify.annotations.Nullable;

public interface IIngredientListOverlay {
    public Optional<ITypedIngredient<?>> getIngredientUnderMouse();

    public <T> @Nullable T getIngredientUnderMouse(IIngredientType<T> var1);

    public boolean isListDisplayed();

    public boolean hasKeyboardFocus();

    public <T> List<T> getVisibleIngredients(IIngredientType<T> var1);
}

