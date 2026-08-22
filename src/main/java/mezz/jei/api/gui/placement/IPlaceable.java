/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.gui.placement;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;

public interface IPlaceable<THIS extends IPlaceable<THIS>> {
    public THIS setPosition(int var1, int var2);

    default public THIS setPosition(int areaX, int areaY, int areaWidth, int areaHeight, HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
        int x = areaX + horizontalAlignment.getXPos(areaWidth, this.getWidth());
        int y = areaY + verticalAlignment.getYPos(areaHeight, this.getHeight());
        return this.setPosition(x, y);
    }

    public int getWidth();

    public int getHeight();
}

