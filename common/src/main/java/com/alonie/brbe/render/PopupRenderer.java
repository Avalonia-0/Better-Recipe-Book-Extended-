package com.alonie.brbe.render;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * 1.21.1 版配方弹窗渲染（1.21.11 PopupRenderer 移植；RecipeHolder/JEI 条目双路径）。
 *
 * <p>JEI 条目（带原生槽位布局）：经 PopupGeometry.adaptedSynthetic 计算 1:1
 * 原始尺寸几何，再委托 headless-jei 的 {@code JeiPopupRenderer.render}
 * （反射桥）完整渲染 JEI 界面（类别背景/槽位/文字），返回面板矩形。
 * 无布局/no-JEI → 回退 {@link #renderVanillaPopup}（1.21.1 轻量布局）。</p>
 *
 * <p>vanilla 配方（RecipeHolder）：PopupGeometry.vanilla 固定 2x 弹窗。
 * 与 1.21.11 的语义差异：无 RecipeDisplay/SlotDisplay——槽位从
 * getIngredients()/getResultItem() 提取。</p>
 */
public final class PopupRenderer {

    /** 弹窗面板尺寸（vanilla 回退布局）。 */
    public static final int POPUP_W = 60;
    public static final int POPUP_H = 60;

    private PopupRenderer() {}

    // -- Mode constants (read from category id) ---------------------------------

    public static final int MODE_CRAFTING = 0;
    public static final int MODE_FURNACE = 1;
    public static final int MODE_STONECUTTING = 2;
    public static final int MODE_SMITHING = 3;
    public static final int MODE_ANVIL = 4;
    public static final int MODE_BREWING = 5;
    public static final int MODE_GRINDSTONE = 6;

    /** Map a category to a popup render mode (fallback crafting). */
    public static int modeFor(String categoryId) {
        if (categoryId == null) return MODE_CRAFTING;
        return switch (categoryId) {
            case "furnace", "fuel" -> MODE_FURNACE;
            case "stonecutting" -> MODE_STONECUTTING;
            case "smithing" -> MODE_SMITHING;
            case "anvil" -> MODE_ANVIL;
            case "brewing" -> MODE_BREWING;
            case "grindstone" -> MODE_GRINDSTONE;
            default -> MODE_CRAFTING;
        };
    }

    /** Render a JEI entry's full UI at 1:1 (adapted-synthetic geometry),
     *  delegating to headless-jei's JeiPopupRenderer (reflection).  Returns the
     *  panel rect, or null when the mod is absent / layout is unavailable. */
    public static int[] renderJeiPopup1to1(GuiGraphics gui, RecipeViewerEngine.JeiEntry entry,
                                           int x, int y, int w, int h) {
        if (entry == null || entry.slots() == null || entry.layoutWidth() <= 0
                || entry.layoutHeight() <= 0) {
            return null;
        }
        PopupGeometry geometry = PopupGeometry.adaptedSynthetic(entry, x, y, w, h);
        int[] rect = renderJeiPopup(gui, entry,
                Math.round(geometry.ox), Math.round(geometry.oy),
                GeometryScale.ORIGINAL, entry.layoutWidth(), entry.layoutHeight());
        if (rect != null) {
            // 面板矩形（几何与渲染对齐：头less 渲染器以内容原点为中心）
            int[] panel = new int[] { geometry.x, geometry.y, geometry.w, geometry.h };
            geometrySlotCache = new GeometryRef(geometry, panel);
            return panel;
        }
        return null;
    }

    private static GeometryRef geometrySlotCache;

    /** (geometry, panel) 对——供 slotStackInPopup 命中判定复用。 */
    public record GeometryRef(PopupGeometry geometry, int[] panel) {}

    public static GeometryRef lastGeometry() {
        return geometrySlotCache;
    }

    /** 1:1 时 renderer fit = min((60*s)/rw,(60*s)/rh) 以 s = max(rw,rh)/60 取得 ≈1.0。 */
    private static final class GeometryScale {
        static final float ORIGINAL = 1f;
        private GeometryScale() {}
    }

    /** Render a JEI-backed entry's full JEI UI (delegated reflectively to the
     *  standalone headless-jei mod's {@code JeiPopupRenderer}).  Returns null
     *  when the mod is absent or the entry has no renderable layout. */
    public static int[] renderJeiPopup(GuiGraphics gui, RecipeViewerEngine.JeiEntry entry,
                                       int x, int y, int w, int h, float scale) {
        try {
            Class<?> registryClass = Class.forName("com.alonie.brbe.jei.api.JeiRecipeRegistry");
            Class<?> entryClass = Class.forName("com.alonie.brbe.jei.api.JeiRecipeRegistry$Entry");
            Class<?> rendererClass = Class.forName("com.alonie.brbe.jei.api.JeiPopupRenderer");
            Object bridgeEntry = entryClass.getConstructor(
                            net.minecraft.resources.ResourceLocation.class,
                            Object.class, List.class, List.class, List.class, int.class, int.class)
                    .newInstance(entry.typeUid(), entry.recipe(),
                            entry.inputs() == null ? List.of() : entry.inputs(),
                            entry.outputs() == null ? List.of() : entry.outputs(),
                            List.of(), 0, 0);
            Object result = rendererClass.getMethod("render", entryClass, GuiGraphics.class,
                            int.class, int.class, int.class, int.class, float.class)
                    .invoke(null, bridgeEntry, gui, x, y, w, h, scale);
            return (int[]) result;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** 1:1 委托：按原始终尺寸渲染（scale 经 fit 公式反推）。 */
    private static int[] renderJeiPopup(GuiGraphics gui, RecipeViewerEngine.JeiEntry entry,
                                        int ox, int oy, float ignored,
                                        int layoutW, int layoutH) {
        // renderer 的 fit = min((60*s)/rw, (60*s)/rh) 且封顶 2*s。
        // 取 s = max(lw,lh)/60 → fit == 1.0（1:1）。
        float s = Math.max(layoutW, layoutH) / 60.0f;
        try {
            Class<?> registryClass = Class.forName("com.alonie.brbe.jei.api.JeiRecipeRegistry");
            Class<?> entryClass = Class.forName("com.alonie.brbe.jei.api.JeiRecipeRegistry$Entry");
            Class<?> rendererClass = Class.forName("com.alonie.brbe.jei.api.JeiPopupRenderer");
            Object bridgeEntry = entryClass.getConstructor(
                            net.minecraft.resources.ResourceLocation.class,
                            Object.class, List.class, List.class, List.class, int.class, int.class)
                    .newInstance(entry.typeUid(), entry.recipe(),
                            entry.inputs() == null ? List.of() : entry.inputs(),
                            entry.outputs() == null ? List.of() : entry.outputs(),
                            entry.slots() == null ? List.of() : entry.slots(), 0, 0);
            // 传内容原点为中心坐标：renderer 内部 ox = x + w/2 - rw*fit/2
            // → 传 (ox + rw/2, oy + rh/2) 作为 x,y（w=h=0 时中心即原点）。
            Object result = rendererClass.getMethod("render", entryClass, GuiGraphics.class,
                            int.class, int.class, int.class, int.class, float.class)
                    .invoke(null, bridgeEntry, gui, ox + layoutW / 2, oy + layoutH / 2,
                            0, 0, s);
            return (int[]) result;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** Vanilla popup (fixed 2x geometric layouts).  Returns the popup's
     *  top-left origin + size. */
    public static int[] renderRecipePopup(GuiGraphics gui,
                                          RecipeHolder<?> holder,
                                          int mode, boolean craftable, boolean partial,
                                          int x, int y, int w, int h,
                                          boolean hover, float scale) {
        PopupGeometry geometry = PopupGeometry.vanilla(holder, mode, null, x, y, w, h);
        gui.pose().pushPose();
        gui.pose().translate(x + w / 2f, y + h / 2f, 0);
        gui.pose().scale(scale, scale, 1.0F);
        gui.pose().translate(-(x + w / 2f), -(y + h / 2f), 0);
        renderVanillaContent(gui, holder, mode, craftable, partial,
                x, y, w, h, hover);
        gui.pose().popPose();
        // 存几何供槽位命中（slotStackInPopup 用；阶段一 #3）。
        int[] panel = new int[] {geometry.x, geometry.y, geometry.w, geometry.h};
        geometrySlotCache = new GeometryRef(geometry, panel);
        return panel;
    }

    /** Vanilla popup content: the recipe-overlay sprite at the button rect, with
     *  slots laid out at button-relative coordinates.  Fixes 1.21.1's previous
     *  absolute-coordinate content (drew off-screen for any tooltip not hugging
     *  the top-left corner, so the 48x48 preview row showed empty — "large empty
     *  dark box").  1.21.11 renderVanillaPopup/renderSlotItems semantics. */
    private static void renderVanillaContent(GuiGraphics gui, RecipeHolder<?> holder,
                                             int mode, boolean craftable, boolean partial,
                                             int x, int y, int w, int h, boolean hover) {
        // 背景 sprite（熔炉类别 plain overlay，其余 crafting overlay；hover/
        // partial 状态由 WidgetSprites.get(craftable||partial, hover) 决定）。
        WidgetSprites sprites = mode == MODE_FURNACE
                ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE
                : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE;
        gui.blitSprite(sprites.get(craftable || partial, hover), x, y, w, h);
        if (partial && mode != MODE_CRAFTING) {
            gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x60FF3333);
        }
        List<ItemStack> inputs = inputsOf(holder);
        ItemStack result = resultOf(holder);
        switch (mode) {
            case MODE_FURNACE -> {
                scaledItem(gui, inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0), x + 2, y + 2);
                gui.blitSprite(BRBTextures.FURNACE_FIRE_SPRITE, x + 4, y + 15, 6, 6);
                if (!result.isEmpty()) scaledItem(gui, result, x + 12, y + 7);
            }
            case MODE_STONECUTTING, MODE_SMITHING, MODE_ANVIL, MODE_BREWING,
                 MODE_GRINDSTONE -> {
                scaledItem(gui, inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0), x + 2, y + 2);
                if (!result.isEmpty()) scaledItem(gui, result, x + 12, y + 7);
            }
            default -> {
                // crafting：3x2 输入（间距 5）+ 结果右上。button 相对（1.21.11
                // renderGenericCrafting 同布局）。
                for (int i = 0; i < Math.min(inputs.size(), 6); i++) {
                    scaledItem(gui, inputs.get(i), x + 2 + (i % 3) * 5, y + 2 + (i / 3) * 5);
                }
                if (!result.isEmpty()) scaledItem(gui, result, x + 17, y + 2);
            }
        }
    }

    /** 0.6-scaled 16px icon (translate is the top-left). */
    private static void scaledItem(GuiGraphics gui, ItemStack stack, int tx, int ty) {
        if (stack.isEmpty()) return;
        gui.pose().pushPose();
        gui.pose().translate(tx, ty, 0);
        gui.pose().scale(0.6f, 0.6f, 1.0F);
        gui.renderItem(stack, 0, 0);
        gui.pose().popPose();
    }

    public static List<ItemStack> inputsOf(RecipeHolder<?> holder) {
        List<ItemStack> out = new java.util.ArrayList<>();
        for (Ingredient ingredient : holder.value().getIngredients()) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) out.add(stacks[0]);
        }
        return out;
    }

    public static ItemStack resultOf(RecipeHolder<?> holder) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return ItemStack.EMPTY;
            return holder.value().getResultItem(mc.level.registryAccess());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** 按给定内容区 (x,y,w,h) 缩放渲染 JEI 条目完整 UI（tooltip 内嵌预览用；
     *  fit = min(w/layoutW, h/layoutH)）。返回 null = 无布局/no-JEI。 */
    public static int[] renderJeiPopupScaled(GuiGraphics gui, RecipeViewerEngine.JeiEntry entry,
                                             int x, int y, int w, int h) {
        if (entry == null || entry.layoutWidth() <= 0 || entry.layoutHeight() <= 0) {
            return null;
        }
        float fit = Math.min(w / (float) entry.layoutWidth(), h / (float) entry.layoutHeight());
        // 借用 1:1 委托：以 fit 为目标（renderer 内部 fit = min((60*s)/rw,(60*s)/rh)
        // 且封顶 2*s——取 s = max(rw,rh)/floor(fit*60... 简化：直接用 1:1 委托并
        // 以内容原点为中心；缩放由调用方 pose 控制。）
        float s = Math.max(entry.layoutWidth(), entry.layoutHeight()) / 60.0f;
        try {
            Class<?> registryClass = Class.forName("com.alonie.brbe.jei.api.JeiRecipeRegistry");
            Class<?> entryClass = Class.forName("com.alonie.brbe.jei.api.JeiRecipeRegistry$Entry");
            Class<?> rendererClass = Class.forName("com.alonie.brbe.jei.api.JeiPopupRenderer");
            Object bridgeEntry = entryClass.getConstructor(
                            net.minecraft.resources.ResourceLocation.class,
                            Object.class, List.class, List.class, List.class, int.class, int.class)
                    .newInstance(entry.typeUid(), entry.recipe(),
                            entry.inputs() == null ? List.of() : entry.inputs(),
                            entry.outputs() == null ? List.of() : entry.outputs(),
                            entry.slots() == null ? List.of() : entry.slots(), 0, 0);
            gui.pose().pushPose();
            gui.pose().translate(x + w / 2f, y + h / 2f, 0);
            gui.pose().scale(fit, fit, 1.0F);
            gui.pose().translate(-(entry.layoutWidth() / 2f), -(entry.layoutHeight() / 2f), 0);
            Object result = rendererClass.getMethod("render", entryClass, GuiGraphics.class,
                            int.class, int.class, int.class, int.class, float.class)
                    .invoke(null, bridgeEntry, gui, 0, 0, 0, 0, s);
            gui.pose().popPose();
            return (int[]) result;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

}