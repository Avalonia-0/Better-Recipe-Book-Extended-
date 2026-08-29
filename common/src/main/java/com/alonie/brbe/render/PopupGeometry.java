package com.alonie.brbe.render;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 版弹窗几何（1.21.11 PopupGeometry 移植；RecipeLayout/SlotDisplay 缺失
 * → 数据源换 {@link RecipeViewerEngine.JeiEntry} 的槽位布局 + RecipeHolder 的
 * Ingredient 列表）。
 *
 * <p>x/y/w/h = 渲染纹理的屏幕边界（命中体积）；ox/oy/fit = 内容坐标系
 * （渲染 = translate(ox,oy) scale(fit)）。</p>
 */
public final class PopupGeometry {

    public static final float VANILLA_SCALE = 2f;
    public static final float CONTENT_ZOOM = 2.2f * 2f;
    public static final int CONTAINER_PADDING = 4;
    private static final float BACKGROUND_FIT = 1.5f;
    public static final float ICON_HALF = 0.6f * 16f / 2f;

    /** One display slot: layout-local centre (icon centre at x+ICON_HALF),
     *  stacks cycle with the selector index. */
    public record Slot(float x, float y, List<ItemStack> stacks, float hitRadius) {}

    public final int x, y, w, h;
    public final float ox, oy, fit;
    public final float contentW, contentH;
    public final List<Slot> slots;

    private PopupGeometry(int x, int y, int w, int h,
                          float ox, float oy, float fit,
                          float contentW, float contentH, List<Slot> slots) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.ox = ox; this.oy = oy; this.fit = fit;
        this.contentW = contentW; this.contentH = contentH;
        this.slots = List.copyOf(slots);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Cursor item at content coordinates (selIdx cycles slot variant). */
    public ItemStack itemAt(double mouseX, double mouseY, int selIdx) {
        double relX = (mouseX - ox) / fit;
        double relY = (mouseY - oy) / fit;
        for (Slot slot : slots) {
            if (Math.abs(relX - slot.x) <= slot.hitRadius
                    && Math.abs(relY - slot.y) <= slot.hitRadius) {
                List<ItemStack> stacks = slot.stacks;
                if (stacks == null || stacks.isEmpty()) return ItemStack.EMPTY;
                return stacks.get(Math.floorMod(selIdx, stacks.size()));
            }
        }
        return ItemStack.EMPTY;
    }

    /** 面板尺寸 1:1（原始像素）或按窗口 80% 钳制。 */
    private static float originalSizeFit(int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return 1f;
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        if (width <= guiW * 0.8f - 8 && height <= guiH * 0.8f - 8) return 1f;
        float maxW = Math.max(1f, guiW * 0.8f - 8);
        float maxH = Math.max(1f, guiH * 0.8f - 8);
        return Math.min(maxW / width, maxH / height);
    }

    /** JEI 条目（带原生槽位布局）：1:1 原始尺寸 + 屏幕钳位。起原点为
     *  24px 按钮矩形。 */
    public static PopupGeometry adaptedSynthetic(RecipeViewerEngine.JeiEntry entry,
                                                 int x, int y, int w, int h) {
        int lw = entry.layoutWidth();
        int lh = entry.layoutHeight();
        List<Slot> out = new ArrayList<>();
        if (entry.slots() != null) {
            for (RecipeViewerEngine.JeiSlot slot : entry.slots()) {
                List<ItemStack> stacks = slot.stacks();
                out.add(new Slot(slot.x() + 8f, slot.y() + 8f, stacks, 8f));
            }
        }
        float fit = originalSizeFit(lw, lh);
        float cw = Math.round(lw * fit);
        float ch = Math.round(lh * fit);
        float ox = x + (w - cw) / 2f;
        float oy = y + (h - ch) / 2f;
        int px = Math.round(ox) - CONTAINER_PADDING;
        int py = Math.round(oy) - CONTAINER_PADDING;
        int pw = Math.round(cw) + CONTAINER_PADDING * 2;
        int ph = Math.round(ch) + CONTAINER_PADDING * 2;
        // 屏幕钳位（面板居中于 24px 按钮；贴边时平移面板并同平移内容原点）
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            int guiW = mc.getWindow().getGuiScaledWidth();
            int guiH = mc.getWindow().getGuiScaledHeight();
            int maxX = Math.max(2, guiW - pw - 2);
            int maxY = Math.max(2, guiH - ph - 2);
            if (px > maxX) { ox -= px - maxX; px = maxX; }
            if (py > maxY) { oy -= py - maxY; py = maxY; }
        }
        return new PopupGeometry(px, py, pw, ph, ox, oy, fit, lw, lh, out);
    }

    /** vanilla 配方（RecipeHolder）：固定 2x 弹窗。 */
    public static PopupGeometry vanilla(RecipeHolder<?> holder, int mode,
                                        List<Slot> externalSlots,
                                        int x, int y, int w, int h) {
        float fit = VANILLA_SCALE;
        float ox = x + w / 2f - w * fit / 2f;
        float oy = y + h / 2f - h * fit / 2f;
        List<Slot> out = new ArrayList<>();
        if (externalSlots != null) {
            out.addAll(externalSlots);
        } else if (mode == PopupRenderer.MODE_CRAFTING) {
            genericCraftingSlots(holder, out);
        } else {
            fixedPairSlots(holder, mode, out);
        }
        int bw = Math.round(w * fit);
        int bh = Math.round(h * fit);
        int bx = Math.round(x + (w - bw) / 2f);
        int by = Math.round(y + (h - bh) / 2f);
        return new PopupGeometry(bx, by, bw, bh, ox, oy, fit, w, h, out);
    }

    /** crafting 无槽位回退：3x2 输入 (间距 5) + 结果右上。 */
    private static void genericCraftingSlots(RecipeHolder<?> holder, List<Slot> out) {
        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();
        try {
            for (Ingredient ingredient : holder.value().getIngredients()) {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks.length > 0) inputs.add(stacks[0]);
            }
            ItemStack result = holder.value().getResultItem(
                    Minecraft.getInstance().level.registryAccess());
            if (!result.isEmpty()) results.add(result);
        } catch (Exception e) {
            return;
        }
        for (int i = 0; i < Math.min(inputs.size(), 6); i++) {
            out.add(new Slot(2 + (i % 3) * 5 + ICON_HALF, 2 + (i / 3) * 5 + ICON_HALF,
                    List.of(inputs.get(i)), 5f));
        }
        if (!results.isEmpty()) {
            out.add(new Slot(17 + ICON_HALF, 2 + ICON_HALF, results, 5f));
        }
    }

    /** 固定双槽（切石/锻造/熔炼）：输入 (2,2) 结果 (12,7)。 */
    private static void fixedPairSlots(RecipeHolder<?> holder, int mode, List<Slot> out) {
        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();
        try {
            for (Ingredient ingredient : holder.value().getIngredients()) {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks.length > 0) inputs.add(stacks[0]);
            }
            ItemStack result = holder.value().getResultItem(
                    Minecraft.getInstance().level.registryAccess());
            if (!result.isEmpty()) results.add(result);
        } catch (Exception e) {
            return;
        }
        if (!inputs.isEmpty()) {
            out.add(new Slot(2 + ICON_HALF, 2 + ICON_HALF, List.of(inputs.get(0)), 5f));
        }
        if (!results.isEmpty()) {
            out.add(new Slot(12 + ICON_HALF, 7 + ICON_HALF, results, 5f));
        }
    }
}
