/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 */
package mezz.jei.api.gui.drawable;

import net.minecraft.client.gui.GuiGraphics;

public interface IDrawable {
    public int getWidth();

    public int getHeight();

    default public void draw(GuiGraphics guiGraphics) {
        this.draw(guiGraphics, 0, 0);
    }

    public void draw(GuiGraphics var1, int var2, int var3);
}

