/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.Screen
 */
package mezz.jei.api.runtime;

import java.util.List;
import java.util.Optional;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.screens.Screen;

public interface IRecipesGui {
    default public <V> void show(IFocus<V> focus) {
        this.show(List.of(focus));
    }

    public void show(List<IFocus<?>> var1);

    public void showTypes(List<IRecipeType<?>> var1);

    public <T> void showRecipes(IRecipeCategory<T> var1, List<T> var2, List<IFocus<?>> var3);

    public <T> Optional<T> getIngredientUnderMouse(IIngredientType<T> var1);

    public Optional<Screen> getParentScreen();
}

