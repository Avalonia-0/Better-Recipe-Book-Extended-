/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.TooltipFlag
 *  org.joml.Matrix3x2fStack
 */
package mezz.jei.api.ingredients;

import java.util.List;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.joml.Matrix3x2fStack;

public interface IIngredientRenderer<T> {
    public void render(GuiGraphics var1, T var2);

    default public void render(GuiGraphics guiGraphics, T ingredient, int posX, int posY) {
        Matrix3x2fStack poseStack = guiGraphics.pose();
        poseStack.pushMatrix();
        poseStack.translate((float)posX, (float)posY);
        this.render(guiGraphics, ingredient);
        poseStack.popMatrix();
    }

    default public void renderBatch(GuiGraphics guiGraphics, List<BatchRenderElement<T>> elements) {
        for (BatchRenderElement<T> element : elements) {
            this.render(guiGraphics, element.ingredient(), element.x(), element.y());
        }
    }

    public List<Component> getTooltip(T var1, TooltipFlag var2);

    default public void getTooltip(ITooltipBuilder tooltip, T ingredient, TooltipFlag tooltipFlag) {
        List<Component> components = this.getTooltip(ingredient, tooltipFlag);
        tooltip.addAll(components);
    }

    default public Font getFontRenderer(Minecraft minecraft, T ingredient) {
        return minecraft.font;
    }

    default public int getWidth() {
        return 16;
    }

    default public int getHeight() {
        return 16;
    }
}

