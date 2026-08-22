/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.recipe.advanced;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import org.jspecify.annotations.Nullable;

public interface IRecipeButtonControllerFactory {
    public <T> @Nullable IIconButtonController createButtonController(IRecipeLayoutDrawable<T> var1);
}

