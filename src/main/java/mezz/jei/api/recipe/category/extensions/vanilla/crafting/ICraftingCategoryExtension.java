/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.crafting.CraftingRecipe
 *  net.minecraft.world.item.crafting.RecipeHolder
 *  net.minecraft.world.item.crafting.display.RecipeDisplay
 *  net.minecraft.world.item.crafting.display.SlotDisplay
 */
package mezz.jei.api.recipe.category.extensions.vanilla.crafting;

import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryExtension;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public interface ICraftingCategoryExtension<R extends CraftingRecipe>
extends IRecipeCategoryExtension<RecipeHolder<R>> {
    public List<SlotDisplay> getIngredients(RecipeHolder<R> var1);

    default public int getWidth(RecipeHolder<R> recipeHolder) {
        return 0;
    }

    default public int getHeight(RecipeHolder<R> recipeHolder) {
        return 0;
    }

    default public void setRecipe(RecipeHolder<R> recipeHolder, IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
        CraftingRecipe recipe = (CraftingRecipe)recipeHolder.value();
        RecipeDisplay display = (RecipeDisplay)recipe.display().getFirst();
        SlotDisplay resultItem = display.result();
        craftingGridHelper.createAndSetOutputs(builder, resultItem);
        List<SlotDisplay> ingredients = this.getIngredients(recipeHolder);
        int width = this.getWidth(recipeHolder);
        int height = this.getHeight(recipeHolder);
        craftingGridHelper.createAndSetIngredientsFromDisplays(builder, ingredients, width, height);
    }

    default public void onDisplayedIngredientsUpdate(RecipeHolder<R> recipeHolder, List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
    }
}

