/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.resources.Identifier
 */
package mezz.jei.common.gui.elements;

import mezz.jei.api.gui.drawable.IDrawableStatic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class DrawableResource
implements IDrawableStatic {
    private final Identifier id;
    private final int textureWidth;
    private final int textureHeight;
    private final int u;
    private final int v;
    private final int width;
    private final int height;
    private final int paddingTop;
    private final int paddingBottom;
    private final int paddingLeft;
    private final int paddingRight;

    public DrawableResource(Identifier id, int u, int v, int width, int height, int paddingTop, int paddingBottom, int paddingLeft, int paddingRight, int textureWidth, int textureHeight) {
        this.id = id;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
        this.paddingTop = paddingTop;
        this.paddingBottom = paddingBottom;
        this.paddingLeft = paddingLeft;
        this.paddingRight = paddingRight;
    }

    @Override
    public int getWidth() {
        return this.width + this.paddingLeft + this.paddingRight;
    }

    @Override
    public int getHeight() {
        return this.height + this.paddingTop + this.paddingBottom;
    }

    @Override
    public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
        this.draw(guiGraphics, xOffset, yOffset, 0, 0, 0, 0);
    }

    @Override
    public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset, int maskTop, int maskBottom, int maskLeft, int maskRight) {
        int uWidth = this.width - (maskRight + maskLeft);
        int vHeight = this.height - (maskBottom + maskTop);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.id, xOffset + maskLeft, yOffset + maskTop, (float)(this.u + maskLeft), (float)(this.v + maskTop), uWidth, vHeight, this.textureWidth, this.textureHeight);
    }
}

