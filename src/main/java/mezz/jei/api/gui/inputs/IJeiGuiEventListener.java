/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.navigation.ScreenRectangle
 */
package mezz.jei.api.gui.inputs;

import net.minecraft.client.gui.navigation.ScreenRectangle;

public interface IJeiGuiEventListener {
    public ScreenRectangle getArea();

    default public void mouseMoved(double mouseX, double mouseY) {
    }

    default public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    default public boolean keyPressed(double mouseX, double mouseY, int keyCode, int scanCode, int modifiers) {
        return false;
    }
}

