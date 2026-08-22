/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.navigation.ScreenRectangle
 */
package mezz.jei.api.gui.widgets;

import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public interface IScrollGridWidget
extends ISlottedRecipeWidget,
IPlaceable<IScrollGridWidget> {
    public ScreenRectangle getScreenRectangle();
}

