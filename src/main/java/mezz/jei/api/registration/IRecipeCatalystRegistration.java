/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 */
package mezz.jei.api.registration;

import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public interface IRecipeCatalystRegistration {
    public IIngredientManager getIngredientManager();

    public IJeiHelpers getJeiHelpers();

    public void addCraftingStation(IRecipeType<?> var1, ItemLike ... var2);

    default public void addCraftingStation(IRecipeType<?> recipeType, ItemStack ... ingredients) {
        this.addCraftingStations(recipeType, VanillaTypes.ITEM_STACK, List.of(ingredients));
    }

    public <T> void addCraftingStation(IRecipeType<?> var1, IIngredientType<T> var2, T var3);

    public <T> void addCraftingStations(IRecipeType<?> var1, IIngredientType<T> var2, List<T> var3);

    @Deprecated(forRemoval=true, since="20.0.0")
    default public void addRecipeCatalysts(IRecipeType<?> recipeType, ItemLike ... ingredients) {
        this.addCraftingStation(recipeType, ingredients);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public void addRecipeCatalysts(IRecipeType<?> recipeType, ItemStack ... ingredients) {
        this.addCraftingStation(recipeType, ingredients);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public <T> void addRecipeCatalysts(IRecipeType<?> recipeType, IIngredientType<T> ingredientType, List<T> ingredients) {
        this.addCraftingStations(recipeType, ingredientType, ingredients);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public void addRecipeCatalyst(ItemLike itemLike, IRecipeType<?> ... recipeTypes) {
        this.addRecipeCatalyst(VanillaTypes.ITEM_STACK, itemLike.asItem().getDefaultInstance(), recipeTypes);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public void addRecipeCatalyst(ItemStack ingredient, IRecipeType<?> ... recipeTypes) {
        this.addRecipeCatalyst(VanillaTypes.ITEM_STACK, ingredient, recipeTypes);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    public <T> void addRecipeCatalyst(IIngredientType<T> var1, T var2, IRecipeType<?> ... var3);
}

