/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.gui.builder;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;

public interface IRecipeLayoutBuilder {
    default public IRecipeSlotBuilder addInputSlot(int x, int y) {
        return (IRecipeSlotBuilder)this.addSlot(RecipeIngredientRole.INPUT).setPosition(x, y);
    }

    default public IRecipeSlotBuilder addInputSlot() {
        return this.addSlot(RecipeIngredientRole.INPUT);
    }

    default public IRecipeSlotBuilder addOutputSlot(int x, int y) {
        return (IRecipeSlotBuilder)this.addSlot(RecipeIngredientRole.OUTPUT).setPosition(x, y);
    }

    default public IRecipeSlotBuilder addOutputSlot() {
        return this.addSlot(RecipeIngredientRole.OUTPUT);
    }

    default public IRecipeSlotBuilder addSlot(RecipeIngredientRole role, int x, int y) {
        return (IRecipeSlotBuilder)this.addSlot(role).setPosition(x, y);
    }

    public IRecipeSlotBuilder addSlot(RecipeIngredientRole var1);

    public IIngredientAcceptor<?> addInvisibleIngredients(RecipeIngredientRole var1);

    public void moveRecipeTransferButton(int var1, int var2);

    public void setShapeless();

    public void setShapeless(int var1, int var2);

    public void createFocusLink(IIngredientAcceptor<?> ... var1);
}

