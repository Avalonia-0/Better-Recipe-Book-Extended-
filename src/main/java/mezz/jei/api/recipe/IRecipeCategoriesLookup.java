/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.recipe;

import java.util.Collection;
import java.util.stream.Stream;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;

public interface IRecipeCategoriesLookup {
    public IRecipeCategoriesLookup limitTypes(Collection<IRecipeType<?>> var1);

    public IRecipeCategoriesLookup limitFocus(Collection<? extends IFocus<?>> var1);

    public IRecipeCategoriesLookup includeHidden();

    public Stream<IRecipeCategory<?>> get();
}

