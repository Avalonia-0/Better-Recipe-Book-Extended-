package com.alonie.brbe.render;

import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.compat.SyntheticRecipeRenderers;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.util.PartialCraftingUtil;
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
        // 不可合成/残缺对象：检索空间物品数量表（可合成对象不画幽灵遮罩）——
        // 与 Shift 预览/pin 同一数据源；无高亮纹理面。
        // 缺料遮罩**只由实时库存逐槽判定**（computeMissing 逐槽扣减），不再用
        // (craftable && !partial) 这对标志去决定"要不要画遮罩"：那两个标志分别由
        // tagger（残缺标记）与 prepareForViewer（craftable 注入）维护，任何一次失配
        // 都会让**整块遮罩一起消失**（用户 2026-09-13：pin 里拿到其中一个材料后，
        // 所有缺料标记全没了）。材料齐全时逐槽判定自然全 false → 不画遮罩，
        // 与旧行为完全一致。
        java.util.Map<net.minecraft.world.item.Item, Integer> counts =
                PartialCraftingUtil.searchSpaceItemCounts();
        if (delegated) {
            RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
            if (layout == null) return;
            // The same painting as the Shift popup: the delegated JEI drawable
            // at its original 1:1 size + the 9-sliced panel.
            SyntheticRecipeRenderers.get().render(id, gui,
                    px + PopupGeometry.CONTAINER_PADDING, py + PopupGeometry.CONTAINER_PADDING,
                    Math.max(1, Math.round(layout.width() * TOOLTIP_SCALE)),
                    Math.max(1, Math.round(layout.height() * TOOLTIP_SCALE)));
            if (counts != null) {
                PopupRenderer.drawDelegatedGhostMasksAt(gui, id,
                        px + PopupGeometry.CONTAINER_PADDING, py + PopupGeometry.CONTAINER_PADDING,
                        TOOLTIP_SCALE, counts);
            }
            // The delegated JEI UI keeps its own look: no partial-crafting red
            // cover on the complete JEI-rendered recipe interface.
            return;
        }
        // Vanilla-style preview (crafting grid / furnace fixed pair / …): the
        // same rendering the Shift popup uses for non-delegated entries.
        PopupRenderer.renderRecipePopup(gui, id, entry, mode, craftable, partial,
                slots, selIdx, px + 24 / 2, py + 24 / 2, 24, 24,
                true, PopupGeometry.VANILLA_SCALE, counts, false);
    }
}
