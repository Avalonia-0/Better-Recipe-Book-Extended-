/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.renderer.Rect2i
 */
package mezz.jei.api.gui.drawable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

public interface IScalableDrawable {
    public void draw(GuiGraphics var1, int var2, int var3, int var4, int var5);

    default public void draw(GuiGraphics guiGraphics, Rect2i area) {
        this.draw(guiGraphics, area.getX(), area.getY(), area.getWidth(), area.getHeight());
    }
}

