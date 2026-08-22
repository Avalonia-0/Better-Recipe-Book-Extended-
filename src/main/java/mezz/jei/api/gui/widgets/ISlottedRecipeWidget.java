/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.gui.widgets;

import java.util.Optional;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.IRecipeWidget;

public interface ISlottedRecipeWidget
extends IRecipeWidget {
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double var1, double var3);
}

