package com.alonie.brbe.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The tooltip's title row with the item's icon to the LEFT of the title text,
 * both vertically centred in the row.  The icon is rendered at <b>150%</b>
 * (24px instead of the vanilla 16px, per user) — this is the standard title
 * row of the query object's tooltip; the preview (popup) item tooltip and the
 * pin item tooltip use the same component/position/scale so every BRBE
 * tooltip's object icon reads identically.
 */
public final class TitleWithIconTooltipComponent implements ClientTooltipComponent {
    /** Vanilla item size x 150% (user: 放大 50%). */
    public static final int ICON_SIZE = 24;
    private static final int GAP = 3;

    private final FormattedCharSequence title;
    private final ItemStack icon;

    public TitleWithIconTooltipComponent(FormattedCharSequence title, ItemStack icon) {
        this.title = title;
        this.icon = icon;
    }

    @Override
    public int getWidth(Font font) {
        return ICON_SIZE + GAP + font.width(title);
    }

    @Override
    public int getHeight(Font font) {
        return Math.max(font.lineHeight, ICON_SIZE);
    }

    @Override
    public void renderText(GuiGraphics gui, Font font, int x, int y) {
        // Centre the title text vertically against the icon row.
        int ty = y + (getHeight(font) - font.lineHeight) / 2;
        gui.drawString(font, title, x + ICON_SIZE + GAP, ty, -1, true);
    }

    @Override
    public void renderImage(Font font, int x, int y, int width, int height,
                            GuiGraphics gui) {
        int iy = y + (getHeight(font) - ICON_SIZE) / 2;
        gui.pose().pushMatrix();
        gui.pose().translate(x, iy);
        gui.pose().scale(1.5f, 1.5f);
        gui.renderItem(icon, 0, 0, 0);
        gui.pose().popMatrix();
    }
}
