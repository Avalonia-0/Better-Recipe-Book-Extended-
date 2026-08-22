/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.FormattedText
 */
package mezz.jei.api.gui.widgets;

import java.util.List;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import mezz.jei.api.gui.widgets.IScrollGridWidget;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.gui.widgets.ITextWidget;
import net.minecraft.network.chat.FormattedText;

public interface IRecipeExtrasBuilder {
    public IRecipeSlotDrawablesView getRecipeSlots();

    public void addDrawable(IDrawable var1, int var2, int var3);

    public IPlaceable<?> addDrawable(IDrawable var1);

    public void addWidget(IRecipeWidget var1);

    public void addSlottedWidget(ISlottedRecipeWidget var1, List<IRecipeSlotDrawable> var2);

    public void addInputHandler(IJeiInputHandler var1);

    public void addGuiEventListener(IJeiGuiEventListener var1);

    public IScrollBoxWidget addScrollBoxWidget(int var1, int var2, int var3, int var4);

    public IScrollGridWidget addScrollGridWidget(List<IRecipeSlotDrawable> var1, int var2, int var3);

    public IPlaceable<?> addRecipeArrow();

    public IPlaceable<?> addRecipePlusSign();

    public IPlaceable<?> addAnimatedRecipeArrow(int var1);

    public IPlaceable<?> addAnimatedRecipeFlame(int var1);

    default public ITextWidget addText(FormattedText text, int maxWidth, int maxHeight) {
        return this.addText(List.of(text), maxWidth, maxHeight);
    }

    public ITextWidget addText(List<FormattedText> var1, int var2, int var3);
}

