/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Key
 *  net.minecraft.client.gui.navigation.ScreenRectangle
 */
package mezz.jei.api.gui.inputs;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public interface IJeiInputHandler {
    public ScreenRectangle getArea();

    default public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        return false;
    }

    default public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        return false;
    }

    default public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        return false;
    }

    default public void handleMouseMoved(double mouseX, double mouseY) {
    }
}

