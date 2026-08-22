/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.gui.placement;

public enum HorizontalAlignment {
    LEFT{

        @Override
        public int getXPos(int availableWidth, int elementWidth) {
            return 0;
        }
    }
    ,
    CENTER{

        @Override
        public int getXPos(int availableWidth, int elementWidth) {
            return Math.round((float)(availableWidth - elementWidth) / 2.0f);
        }
    }
    ,
    RIGHT{

        @Override
        public int getXPos(int availableWidth, int elementWidth) {
            return availableWidth - elementWidth;
        }
    };


    public abstract int getXPos(int var1, int var2);
}

