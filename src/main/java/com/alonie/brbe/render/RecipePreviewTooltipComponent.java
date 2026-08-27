package com.alonie.brbe.render;

import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.compat.SyntheticRecipeRenderers;
import com.alonie.brbe.pinoverlay.PinOverlay;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.List;

/**
 * A tooltip row embedding the recipe's full preview UI inside the hover
 * tooltip of a query-viewer object (no Shift) — the same painting the Shift
 * popup uses.  Delegateable recipes render the complete JEI UI at its
 * original 1:1 size inside the 9-sliced panel; everything else (the BRBE
 * vanilla-style previews: crafting grid / furnace fixed pair, …) renders the
 * vanilla hover-scaled popup.  The row's size is the preview panel's size
 * (JEI layout + panel padding, or 48x48 for the vanilla popup), so the
 * tooltip widens to hold the real recipe UI instead of only text.
 */
public final class RecipePreviewTooltipComponent implements ClientTooltipComponent {

    /** Tooltip embedded JEI previews render at 60% of the original size
     *  (40% smaller than the Shift popup's 1:1), so the tooltip stays compact. */
    private static final float TOOLTIP_SCALE = 0.6f;

    private final RecipeDisplayId id;
    private final RecipeDisplayEntry entry;
    private final int mode;
    private final List<?> slots;
    private final int selIdx;
    private final boolean craftable;
    private final boolean partial;
    private final boolean delegated;
    private final int width;
    private final int height;

    public RecipePreviewTooltipComponent(RecipeDisplayId id, RecipeDisplayEntry entry, int mode,
                                         List<?> slots, int selIdx, boolean craftable,
                                         boolean partial) {
        this.id = id;
        this.entry = entry;
        this.mode = mode;
        this.slots = slots;
        this.selIdx = selIdx;
        this.craftable = craftable;
        this.partial = partial;
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        this.delegated = SyntheticRecipeRenderers.get() != SyntheticRecipeRenderer.NONE
                && SyntheticRecipeRenderers.get().canRender(id);
        if (delegated && layout != null) {
            this.width = Math.round(layout.width() * TOOLTIP_SCALE)
                    + PopupGeometry.CONTAINER_PADDING * 2;
            this.height = Math.round(layout.height() * TOOLTIP_SCALE)
                    + PopupGeometry.CONTAINER_PADDING * 2;
        } else {
            // Vanilla hover-scaled popup: the 24px button at 2x.
            this.width = Math.round(24 * PopupGeometry.VANILLA_SCALE);
            this.height = Math.round(24 * PopupGeometry.VANILLA_SCALE);
        }
    }

    @Override
    public int getWidth(Font font) {
        return width;
    }

    @Override
    public int getHeight(Font font) {
        return height;
    }

    @Override
    public void renderText(GuiGraphics gui, Font font, int x, int y) {
        // the preview paints everything (slots and text included) in renderImage
    }

    @Override
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics gui) {
        // Note: the width/height parameters are the WHOLE tooltip's size, not
        // this row's — the row starts exactly at (x, y), so position the
        // preview with y as its top and only centre it horizontally.
        // Left-aligned in the tooltip row (the row spans the whole tooltip
        // width); the width/height parameters are the WHOLE tooltip's size,
        // not this row's — the row starts exactly at (x, y).
        int px = x;
        int py = y;
        if (delegated) {
            RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
            if (layout == null) return;
            // The same painting as the Shift popup: the delegated JEI drawable
            // at its original 1:1 size + the 9-sliced panel.
            SyntheticRecipeRenderers.get().render(id, gui,
                    px + PopupGeometry.CONTAINER_PADDING, py + PopupGeometry.CONTAINER_PADDING,
                    Math.max(1, Math.round(layout.width() * TOOLTIP_SCALE)),
                    Math.max(1, Math.round(layout.height() * TOOLTIP_SCALE)));
            // Keep the partial-crafting red cover the popup draws (non-crafting
            // modes only, exactly like the popup's delegate branch).
            if (partial && mode != PinOverlay.MODE_CRAFTING) {
                gui.fill(px, py, px + this.width, py + this.height, 0x60FF3333);
            }
            return;
        }
        // Vanilla-style preview (crafting grid / furnace fixed pair / …): the
        // same rendering the Shift popup uses for non-delegated entries.
        PopupRenderer.renderRecipePopup(gui, id, entry, mode, craftable, partial,
                slots, selIdx, px + 24 / 2, py + 24 / 2, 24, 24,
                true, PopupGeometry.VANILLA_SCALE);
    }
}
