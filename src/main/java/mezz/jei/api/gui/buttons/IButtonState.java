/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.gui.buttons;

import mezz.jei.api.gui.drawable.IDrawable;

public interface IButtonState {
    public void setIcon(IDrawable var1);

    public void setActive(boolean var1);

    public void setVisible(boolean var1);

    public void setForcePressed(boolean var1);
}

