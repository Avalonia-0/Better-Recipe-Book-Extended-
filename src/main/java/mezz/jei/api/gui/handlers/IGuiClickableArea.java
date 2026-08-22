/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.Rect2i
 */
package mezz.jei.api.gui.handlers;

import java.util.Arrays;
import java.util.List;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.renderer.Rect2i;

public interface IGuiClickableArea {
    public Rect2i getArea();

    default public boolean isTooltipEnabled() {
        return true;
    }

    default public void getTooltip(ITooltipBuilder tooltip) {
    }

    public void onClick(IFocusFactory var1, IRecipesGui var2);

    public static IGuiClickableArea createBasic(int xPos, int yPos, int width, int height, IRecipeType<?> ... recipeTypes) {
        final Rect2i area = new Rect2i(xPos, yPos, width, height);
        final List<IRecipeType<?>> recipeTypesList = Arrays.asList(recipeTypes);
        return new IGuiClickableArea(){

            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void onClick(IFocusFactory focusFactory, IRecipesGui recipesGui) {
                recipesGui.showTypes(recipeTypesList);
            }
        };
    }
}

