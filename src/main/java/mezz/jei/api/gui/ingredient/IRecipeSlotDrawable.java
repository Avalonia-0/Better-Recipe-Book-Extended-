/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.renderer.Rect2i
 *  net.minecraft.network.chat.Component
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 */
package mezz.jei.api.gui.ingredient;

import java.util.List;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface IRecipeSlotDrawable
extends IRecipeSlotView {
    public void draw(GuiGraphics var1);

    public void drawHoverOverlays(GuiGraphics var1);

    @Deprecated(since="21.1.0", forRemoval=true)
    public List<Component> getTooltip();

    @Deprecated(since="21.1.0", forRemoval=true)
    public void getTooltip(ITooltipBuilder var1);

    public void drawTooltip(GuiGraphics var1, int var2, int var3);

    public boolean isMouseOver(double var1, double var3);

    public void setPosition(int var1, int var2);

    public IIngredientAcceptor<?> createDisplayOverrides();

    public void clearDisplayOverrides();

    public Rect2i getAreaIncludingBackground();
}

