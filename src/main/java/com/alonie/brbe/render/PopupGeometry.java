package com.alonie.brbe.render;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.compat.SyntheticRecipeRenderers;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonPosAccessor;
import com.alonie.brbe.pinoverlay.PinOverlay;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared popup geometry for the hover UI and the pin UI.  Both front-ends —
 * the BRBE-adapted recipes (vanilla crafting / furnace / stonecutter /
 * smithing and unadapted synthetic) and the JEI-plugin recipes (adapted
 * synthetic, delegated to the companion renderer) — render through one
 * coordinate model, so hit testing, tooltip avoidance and JEI exclusion all
 * read the same geometry:
 *
 * <ul>
 *   <li>{@link #x}/{@link #y}/{@link #w}/{@link #h} — the on-screen bounds of
 *       the rendered texture (the popup's hit volume, equal to the texture
 *       size);</li>
 *   <li>{@link #ox}/{@link #oy}/{@link #fit} — the content coordinate system:
 *       rendering is {@code translate(ox, oy) scale(fit)} of content
 *       coordinates, so {@link #itemAt} un-zooms the cursor by {@code (p -
 *       o)/fit}.</li>
 * </ul>
 *
 * <p>Every presentation constant lives here (single source): the vanilla
 * popup's fixed 2x scale (there is no Shift magnify any more), the JEI
 * content zoom and the container-panel padding.</p>
 */
public final class PopupGeometry {

    /** Vanilla / unadapted popup: fixed 2x scale about the button centre.
     *  (Formerly 2x rest / 4x with Shift; the magnify is gone.) */
    public static final float VANILLA_SCALE = 2f;
    /** JEI content zoom: the category fitted into the button, then scaled up
     *  by 2.2x x 2 so the recipe reads clearly (no Shift variation). */
    public static final float CONTENT_ZOOM = 2.2f * 2f;
    /** The JEI container panel's padding around the content (9-sliced). */
    public static final int CONTAINER_PADDING = 4;
    /** Unadapted synthetic background texture: button-fit x 1.5 (mirrors
     *  {@code ButtonBackdrop.Texture}). */
    private static final float BACKGROUND_FIT = 1.5f;

    /** One interactive slot in content coordinates: its centre, every variant
     *  stack it cycles through, and its hit radius (content units). */
    public record Slot(float x, float y, List<ItemStack> stacks, float hitRadius) {}

    /** Hit volume — the rendered texture's on-screen bounds. */
    public final int x;
    public final int y;
    public final int w;
    public final int h;
    /** Content origin and scale (rendering = translate(ox,oy) scale(fit)). */
    public final float ox;
    public final float oy;
    public final float fit;
    /** Content size (unscaled content units). */
    public final int contentW;
    public final int contentH;

    private final List<Slot> slots;

    private PopupGeometry(int x, int y, int w, int h, float ox, float oy, float fit,
                          int contentW, int contentH, List<Slot> slots) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.ox = ox;
        this.oy = oy;
        this.fit = fit;
        this.contentW = contentW;
        this.contentH = contentH;
        this.slots = slots;
    }

    /** Whether the point lies inside the popup's hit volume. */
    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The item under the cursor (its current cycled variant), or EMPTY. */
    public ItemStack itemAt(double mx, double my, int selIdx) {
        if (slots.isEmpty()) return ItemStack.EMPTY;
        double relX = (mx - ox) / fit;
        double relY = (my - oy) / fit;
        for (Slot slot : slots) {
            if (Math.abs(relX - slot.x()) <= slot.hitRadius()
                    && Math.abs(relY - slot.y()) <= slot.hitRadius()) {
                List<ItemStack> stacks = slot.stacks();
                if (stacks.isEmpty()) return ItemStack.EMPTY;
                return stacks.get(selIdx % stacks.size());
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Build the geometry for a recipe button rect {@code (x, y, w, h)}.
     * {@code mode} selects the vanilla fixed-pair layout (furnace / stonecutter
     * / smithing) vs the crafting grid; {@code slots} is the button accessor's
     * slot list (crafting only).  Adapted synthetic recipes use their native
     * JEI layout; everything else renders through the vanilla fixed 2x scale.
     */
    public static PopupGeometry of(RecipeDisplayId id, RecipeDisplayEntry entry, int mode,
                                   List<?> slots, int x, int y, int w, int h) {
        if (RecipeViewerEngine.isSynthetic(id)
                && SyntheticRecipeRenderers.get() != SyntheticRecipeRenderer.NONE
                && SyntheticRecipeRenderers.get().canRender(id)) {
            PopupGeometry adapted = adaptedSynthetic(id, x, y, w, h);
            if (adapted != null) return adapted;
            // Layout missing: fall through to the vanilla-style rendering.
        }
        return vanilla(id, entry, mode, slots, x, y, w, h);
    }

    /** Adapted synthetic: native layout at CONTENT_ZOOM inside the 9-sliced
     *  panel.  Null when the recipe has no usable layout. */
    private static PopupGeometry adaptedSynthetic(RecipeDisplayId id, int x, int y, int w, int h) {
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        if (layout == null || layout.width() <= 0 || layout.height() <= 0) return null;
        float fit = Math.min(w / (float) layout.width(), h / (float) layout.height())
                * CONTENT_ZOOM;
        int cw = Math.round(layout.width() * fit);
        int ch = Math.round(layout.height() * fit);
        float ox = x + (w - cw) / 2f;
        float oy = y + (h - ch) / 2f;
        int lw = layout.width();
        int lh = layout.height();
        // The 9-sliced panel wraps the content with CONTAINER_PADDING on every
        // side — that panel is the rendered texture and the hit volume.  The
        // rounding matches the renderer's (Math.round on the same origin).
        int px = Math.round(ox) - CONTAINER_PADDING;
        int py = Math.round(oy) - CONTAINER_PADDING;
        int pw = cw + CONTAINER_PADDING * 2;
        int ph = ch + CONTAINER_PADDING * 2;
        List<Slot> out = new ArrayList<>();
        for (RecipeViewerEngine.RecipeSlotLayout slot : layout.slots()) {
            if (slot.stacks().isEmpty()) continue;
            // Slot x/y is the 16px icon's top-left; its centre is +8.
            out.add(new Slot(slot.x() + 8, slot.y() + 8, slot.stacks(), 8));
        }
        return new PopupGeometry(px, py, pw, ph, ox, oy, fit, lw, lh, out);
    }

    /** Vanilla-style geometry: the fixed 2x scale about the button centre.
     *  Content coordinates are button-relative (0..24, the button box). */
    private static PopupGeometry vanilla(RecipeDisplayId id, RecipeDisplayEntry entry,
                                         int mode, List<?> slots, int x, int y, int w, int h) {
        float fit = VANILLA_SCALE;
        float ox = x + w / 2f - w * fit / 2f;
        float oy = y + h / 2f - h * fit / 2f;
        List<Slot> out = new ArrayList<>();

        if (RecipeViewerEngine.isSynthetic(id)) {
            // Unadapted synthetic: fitted native slot positions (or the result
            // icon alone when the recipe has no native layout).
            RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
            List<UnadaptedSlot> fitted = layout != null && layout.background() != null
                    ? backgroundFitPositions(layout) : slotFitPositions(layout);
            for (UnadaptedSlot slot : fitted) {
                out.add(new Slot(slot.x(), slot.y(), slot.stacks(), 5));
            }
            if (out.isEmpty() && entry != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    try {
                        List<ItemStack> results =
                                entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
                        if (!results.isEmpty()) out.add(new Slot(12, 12, results, 5));
                    } catch (Exception ignored) {
                        // unresolvable result — no tracked item
                    }
                }
            }
            // Unadapted synthetic with a declared background texture renders
            // that texture (button-fit x 1.5) instead of the uniform sprite:
            // the hit volume is the texture's region, not the scaled button box.
            if (layout != null && layout.background() != null) {
                RecipeViewerEngine.RecipeBackground bg = layout.background();
                float tscale = Math.min(24f / bg.width(), 24f / bg.height()) * BACKGROUND_FIT;
                int bw = Math.round(bg.width() * tscale * fit);
                int bh = Math.round(bg.height() * tscale * fit);
                return new PopupGeometry(x - 12, y - 12, bw, bh, ox, oy, fit, w, h, out);
            }
        } else if (mode == PinOverlay.MODE_CRAFTING && slots != null && !slots.isEmpty()) {
            // Crafting grid: icons centred at (2+pos.x, 2+pos.y).
            for (Object raw : slots) {
                OverlayRecipeButtonPosAccessor pos = (OverlayRecipeButtonPosAccessor) raw;
                out.add(new Slot(pos.brbe$getX() + 2, pos.brbe$getY() + 2,
                        pos.brbe$getIngredients(), 5));
            }
        } else if (mode == PinOverlay.MODE_CRAFTING) {
            // Displays without vanilla button slots (e.g. Farmer's Delight
            // cooking recipes): build the hit volume from the generic layout,
            // matching PopupRenderer's generic crafting rendering.
            genericCraftingSlots(entry, out);
        } else {
            // Furnace / stonecutter / smithing: fixed input/result positions.
            fixedPairSlots(entry, mode, out);
        }
        int bw = Math.round(w * fit);
        int bh = Math.round(h * fit);
        int bx = Math.round(x + (w - bw) / 2f);
        int by = Math.round(y + (h - bh) / 2f);
        return new PopupGeometry(bx, by, bw, bh, ox, oy, fit, w, h, out);
    }

    /** Generic hit slots for displays without vanilla button slots: the
     *  craftingRequirements inputs on a 3x2 grid plus the result top-right. */
    private static void genericCraftingSlots(RecipeDisplayEntry entry, List<Slot> out) {
        try {
            if (entry == null) return;
            java.util.Optional<List<net.minecraft.world.item.crafting.Ingredient>> reqs =
                    entry.craftingRequirements();
            if (reqs.isPresent()) {
                List<net.minecraft.world.item.crafting.Ingredient> list = reqs.get();
                for (int i = 0; i < Math.min(list.size(), 6); i++) {
                    net.minecraft.world.item.crafting.Ingredient ing = list.get(i);
                    List<ItemStack> stacks = new ArrayList<>();
                    ing.items().forEach(holder -> stacks.add(new ItemStack(holder.value())));
                    if (!stacks.isEmpty()) {
                        out.add(new Slot(2 + (i % 3) * 5, 2 + (i / 3) * 5, stacks, 5));
                    }
                }
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
                if (!results.isEmpty()) out.add(new Slot(17, 2, results, 5));
            }
        } catch (Exception ignored) {
            // one broken ingredient must not break the hit volume
        }
    }

    private static void fixedPairSlots(RecipeDisplayEntry entry, int mode, List<Slot> out) {
        try {
            if (entry == null) return;
            if (mode == PinOverlay.MODE_FURNACE
                    && entry.display() instanceof FurnaceRecipeDisplay furnace) {
                addSlot(out, 2, 2, furnace.ingredient());
                addSlot(out, 12, 7, furnace.result());
            } else if (mode == PinOverlay.MODE_STONECUTTING
                    && entry.display() instanceof StonecutterRecipeDisplay stonecutter) {
                addSlot(out, 2, 2, stonecutter.input());
                addSlot(out, 12, 7, stonecutter.result());
            } else if (mode == PinOverlay.MODE_SMITHING
                    && entry.display() instanceof SmithingRecipeDisplay smithing) {
                addSlot(out, 2, 2, smithing.base());
                addSlot(out, 12, 7, smithing.result());
            }
        } catch (Exception ignored) {
            // unresolvable — no tracked items
        }
    }

    private static void addSlot(List<Slot> out, int x, int y, SlotDisplay display) {
        List<ItemStack> stacks = RecipeViewerIndex.resolveSlotDisplay(display);
        if (!stacks.isEmpty()) {
            out.add(new Slot(x, y, stacks, 5));
        }
    }

    /** One non-empty slot of an unadapted synthetic recipe, at its fitted
     *  button-relative position (icon centre, un-zoomed). */
    public record UnadaptedSlot(float x, float y, List<ItemStack> stacks) {}

    /** The button-relative slot positions of an unadapted synthetic recipe,
     *  fit into the button's 22x22 inner area — the exact geometry used when
     *  no background texture is painted.  Shared so the hit test and the
     *  render cannot drift apart. */
    public static List<UnadaptedSlot> slotFitPositions(RecipeViewerEngine.RecipeLayout layout) {
        List<UnadaptedSlot> out = new ArrayList<>();
        if (layout == null || layout.slots().isEmpty()) return out;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (RecipeViewerEngine.RecipeSlotLayout slot : layout.slots()) {
            minX = Math.min(minX, slot.x());
            maxX = Math.max(maxX, slot.x());
            minY = Math.min(minY, slot.y());
            maxY = Math.max(maxY, slot.y());
        }
        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);
        float fit = Math.min(22f / spanX, 22f / spanY);
        float offX = (22f - spanX * fit) / 2f;
        float offY = (22f - spanY * fit) / 2f;
        for (RecipeViewerEngine.RecipeSlotLayout slot : layout.slots()) {
            if (slot.stacks().isEmpty()) continue;
            out.add(new UnadaptedSlot(1f + offX + (slot.x() - minX) * fit,
                    1f + offY + (slot.y() - minY) * fit, slot.stacks()));
        }
        return out;
    }

    /** Slot positions laid out in the background texture's own fit — mirrors
     *  {@code ButtonBackdrop.Texture} (button 24x24 fit x 1.5), so each slot
     *  lines up with where the texture paints it.  Only valid while that
     *  texture is actually drawn (hovered), which is always true for a popup. */
    public static List<UnadaptedSlot> backgroundFitPositions(RecipeViewerEngine.RecipeLayout layout) {
        List<UnadaptedSlot> out = new ArrayList<>();
        if (layout == null || layout.background() == null) return out;
        RecipeViewerEngine.RecipeBackground bg = layout.background();
        float scale = Math.min(24f / bg.width(), 24f / bg.height()) * BACKGROUND_FIT;
        for (RecipeViewerEngine.RecipeSlotLayout slot : layout.slots()) {
            if (slot.stacks().isEmpty()) continue;
            out.add(new UnadaptedSlot(slot.x() * scale, slot.y() * scale, slot.stacks()));
        }
        return out;
    }
}
