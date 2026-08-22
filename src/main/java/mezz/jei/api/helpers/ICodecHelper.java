/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.Codec
 *  com.mojang.serialization.MapCodec
 *  net.minecraft.world.item.crafting.RecipeHolder
 */
package mezz.jei.api.helpers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;

public interface ICodecHelper {
    public Codec<IIngredientType<?>> getIngredientTypeCodec();

    public Codec<IRecipeType<?>> getRecipeTypeCodec(IRecipeManager var1);

    public MapCodec<ITypedIngredient<?>> getTypedIngredientCodec();

    public <T> Codec<ITypedIngredient<T>> getTypedIngredientCodec(IIngredientType<T> var1);

    public <T extends RecipeHolder<?>> Codec<T> getRecipeHolderCodec();

    public <T> Codec<T> getSlowRecipeCategoryCodec(IRecipeCategory<T> var1, IRecipeManager var2);
}

