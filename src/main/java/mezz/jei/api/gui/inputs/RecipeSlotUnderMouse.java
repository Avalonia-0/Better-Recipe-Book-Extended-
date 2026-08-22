/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.navigation.ScreenPosition
 */
package mezz.jei.api.gui.inputs;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.gui.navigation.ScreenPosition;

public record RecipeSlotUnderMouse(IRecipeSlotDrawable slot, ScreenPosition offset) {
    public RecipeSlotUnderMouse(IRecipeSlotDrawable slot, int xOffset, int yOffset) {
        this(slot, new ScreenPosition(xOffset, yOffset));
    }

    public RecipeSlotUnderMouse addOffset(int xOffset, int yOffset) {
        return new RecipeSlotUnderMouse(this.slot, this.offset.x() + xOffset, this.offset.y() + yOffset);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        double relativeMouseX = mouseX - (double)this.offset.x();
        double relativeMouseY = mouseY - (double)this.offset.y();
        return this.slot.isMouseOver(relativeMouseX, relativeMouseY);
    }
}

