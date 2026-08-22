/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 */
package mezz.jei.api.recipe.category.extensions;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.client.gui.GuiGraphics;

public interface IRecipeCategoryExtension<T> {
    default public void drawInfo(T recipe, int recipeWidth, int recipeHeight, GuiGraphics guiGraphics, double mouseX, double mouseY) {
    }

    default public void getTooltip(ITooltipBuilder tooltip, T recipe, double mouseX, double mouseY) {
    }

    default public void createRecipeExtras(T recipe, IRecipeExtrasBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
    }

    default public boolean isHandled(T recipe) {
        return true;
    }
}

