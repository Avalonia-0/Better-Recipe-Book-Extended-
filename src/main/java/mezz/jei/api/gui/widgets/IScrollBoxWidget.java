/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.FormattedText
 */
package mezz.jei.api.gui.widgets;

import java.util.List;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.network.chat.FormattedText;

public interface IScrollBoxWidget
extends IRecipeWidget,
IJeiInputHandler {
    public int getContentAreaWidth();

    public int getContentAreaHeight();

    public IScrollBoxWidget setContents(IDrawable var1);

    public IScrollBoxWidget setContents(List<FormattedText> var1);
}

