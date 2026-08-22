/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.navigation.ScreenPosition
 */
package mezz.jei.api.gui.widgets;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;

public interface IRecipeWidget {
    public ScreenPosition getPosition();

    default public void drawWidget(GuiGraphics guiGraphics, double mouseX, double mouseY) {
    }

    default public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
    }

    default public void tick() {
    }
}

