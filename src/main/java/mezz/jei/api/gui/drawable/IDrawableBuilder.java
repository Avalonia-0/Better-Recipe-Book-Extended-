/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.gui.drawable;

import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;

public interface IDrawableBuilder {
    public IDrawableBuilder setTextureSize(int var1, int var2);

    public IDrawableBuilder addPadding(int var1, int var2, int var3, int var4);

    public IDrawableBuilder trim(int var1, int var2, int var3, int var4);

    public IDrawableStatic build();

    public IDrawableAnimated buildAnimated(int var1, IDrawableAnimated.StartDirection var2, boolean var3);

    public IDrawableAnimated buildAnimated(ITickTimer var1, IDrawableAnimated.StartDirection var2);
}

