/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.renderer.Rect2i
 */
package mezz.jei.api.gui.handlers;

import java.util.List;
import java.util.function.Consumer;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

public interface IGhostIngredientHandler<T extends Screen> {
    public <I> List<Target<I>> getTargetsTyped(T var1, ITypedIngredient<I> var2, boolean var3);

    public void onComplete();

    default public boolean shouldHighlightTargets() {
        return true;
    }

    public static interface Target<I>
    extends Consumer<I> {
        public Rect2i getArea();

        @Override
        public void accept(I var1);
    }
}

