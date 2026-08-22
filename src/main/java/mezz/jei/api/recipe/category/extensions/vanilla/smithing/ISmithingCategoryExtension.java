/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.crafting.SmithingRecipe
 */
package mezz.jei.api.recipe.category.extensions.vanilla.smithing;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.world.item.crafting.SmithingRecipe;

public interface ISmithingCategoryExtension<R extends SmithingRecipe> {
    public <T extends IIngredientAcceptor<T>> void setTemplate(R var1, T var2);

    public <T extends IIngredientAcceptor<T>> void setBase(R var1, T var2);

    public <T extends IIngredientAcceptor<T>> void setAddition(R var1, T var2);

    default public <T extends IIngredientAcceptor<T>> void setOutput(R recipe, T ingredientAcceptor) {
    }

    default public void onDisplayedIngredientsUpdate(R recipe, IRecipeSlotDrawable templateSlot, IRecipeSlotDrawable baseSlot, IRecipeSlotDrawable additionSlot, IRecipeSlotDrawable outputSlot, IFocusGroup focuses) {
    }
}

