/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.gui.ingredient;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;

@FunctionalInterface
public interface IRecipeSlotRichTooltipCallback {
    public void onRichTooltip(IRecipeSlotView var1, ITooltipBuilder var2);
}

