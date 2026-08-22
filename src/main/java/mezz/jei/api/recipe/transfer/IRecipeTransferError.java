/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 */
package mezz.jei.api.recipe.transfer;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.client.gui.GuiGraphics;

public interface IRecipeTransferError {
    public Type getType();

    default public int getButtonHighlightColor() {
        return -2130729728;
    }

    default public void showError(GuiGraphics guiGraphics, int mouseX, int mouseY, IRecipeSlotsView recipeSlotsView, int recipeX, int recipeY) {
    }

    default public void getTooltip(ITooltipBuilder tooltip) {
    }

    default public int getMissingCountHint() {
        return -1;
    }

    public static enum Type {
        INTERNAL(false),
        USER_FACING(false),
        COSMETIC(true);

        public final boolean allowsTransfer;

        private Type(boolean allowsTransfer) {
            this.allowsTransfer = allowsTransfer;
        }
    }
}

