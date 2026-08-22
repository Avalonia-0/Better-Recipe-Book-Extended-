/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 */
package mezz.jei.api.recipe;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.ICraftingStationLookup;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeCatalystLookup;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.resources.Identifier;

public interface IRecipeManager {
    public <R> IRecipeLookup<R> createRecipeLookup(IRecipeType<R> var1);

    public IRecipeCategoriesLookup createRecipeCategoryLookup();

    public <T> IRecipeCategory<T> getRecipeCategory(IRecipeType<T> var1);

    @Deprecated(forRemoval=true, since="20.0.0")
    public IRecipeCatalystLookup createRecipeCatalystLookup(IRecipeType<?> var1);

    public ICraftingStationLookup createCraftingStationLookup(IRecipeType<?> var1);

    public <T> void hideRecipes(IRecipeType<T> var1, Collection<T> var2);

    public <T> void unhideRecipes(IRecipeType<T> var1, Collection<T> var2);

    public <T> void addRecipes(IRecipeType<T> var1, List<T> var2);

    public void hideRecipeCategory(IRecipeType<?> var1);

    public void unhideRecipeCategory(IRecipeType<?> var1);

    public <T> IRecipeLayoutDrawable<T> createRecipeLayoutDrawableOrShowError(IRecipeCategory<T> var1, T var2, IFocusGroup var3);

    public <T> Optional<IRecipeLayoutDrawable<T>> createRecipeLayoutDrawable(IRecipeCategory<T> var1, T var2, IFocusGroup var3);

    public <T> Optional<IRecipeLayoutDrawable<T>> createRecipeLayoutDrawable(IRecipeCategory<T> var1, T var2, IFocusGroup var3, IScalableDrawable var4, int var5);

    public IRecipeSlotDrawable createRecipeSlotDrawable(RecipeIngredientRole var1, List<Optional<ITypedIngredient<?>>> var2, Set<Integer> var3, int var4);

    public <T> IIngredientSupplier getRecipeIngredients(IRecipeCategory<T> var1, T var2);

    public <T> Optional<IRecipeType<T>> getRecipeType(Identifier var1, Class<? extends T> var2);

    public Optional<IRecipeType<?>> getRecipeType(Identifier var1);

    public List<IRecipeButtonControllerFactory> getRecipeButtonControllerFactories();
}

