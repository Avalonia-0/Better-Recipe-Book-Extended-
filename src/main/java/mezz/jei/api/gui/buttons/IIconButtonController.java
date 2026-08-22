/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.renderer.Rect2i
 */
package mezz.jei.api.gui.buttons;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

public interface IIconButtonController {
    public boolean onPress(IJeiUserInput var1);

    default public void getTooltips(ITooltipBuilder tooltip) {
    }

    default public void initState(IButtonState state) {
        this.updateState(state);
    }

    default public void updateState(IButtonState state) {
    }

    default public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTicks) {
    }
}

