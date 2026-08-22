/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.Screen
 */
package mezz.jei.api.gui.handlers;

import net.minecraft.client.gui.screens.Screen;

public interface IGuiProperties {
    public Class<? extends Screen> screenClass();

    public int guiLeft();

    public int guiTop();

    public int guiXSize();

    public int guiYSize();

    public int screenWidth();

    public int screenHeight();
}

