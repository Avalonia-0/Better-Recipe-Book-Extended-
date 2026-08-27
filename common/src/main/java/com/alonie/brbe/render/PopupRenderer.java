package com.alonie.brbe.render;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Optional;

/**
 * 1.21.1 版配方弹窗渲染（Shift 预览）。
 *
 * <p>对照 1.21.11 的 PopupRenderer：本版按旧 Recipe 模型（RecipeHolder +
 * getIngredients/getResultItem）渲染固定布局——crafting 网格（3x2 输入+结果）、
 * furnace 双槽（输入+火焰+结果）、stonecutting/smithing 双槽、generic 条目行。
 * JEI 条目（无头核心采集的 anvil/grindstone/mod 配方）经 {@link #renderJeiPopup}
 * 委托 IRecipeManager#createRecipeLayoutDrawable 渲染完整 JEI 界面。</p>
 */
public final class PopupRenderer {

    /** 弹窗面板尺寸（加大版预览）。 */
    public static final int POPUP_W = 60;
    public static final int POPUP_H = 60;

    /** 清空 JEI 布局缓存（索引重建时调用；独立项目渲染器在
     *  {@link #renderJeiPopup} 时自动 Re-JEINIT——此钩子保留为 no-op）。 */
    public static void invalidateJeiCache() {
    }

    private PopupRenderer() {}

    /**
     * Render the recipe as a fixed 48x48 popup centered at {@code x+24, y+24}
     * (button anchor); returns the popup's top-left origin.
     */
    public static int[] renderRecipePopup(GuiGraphics gui,
                                          RecipeHolder<?> holder,
                                          int mode, boolean craftable, boolean partial,
                                          int x, int y, int w, int h,
                                          boolean hover, float scale) {
        int ox = (int) (x + w / 2f - 24 * scale);
        int oy = (int) (y + h / 2f - 24 * scale);
        gui.pose().pushPose();
        gui.pose().translate(ox, oy, 0);
        gui.pose().scale(scale, scale, 1.0F);
        renderContent(gui, holder, mode, hover);
        gui.pose().popPose();
        return new int[] {ox, oy, (int) (48 * scale), (int) (48 * scale)};
    }

    private static void renderContent(GuiGraphics gui, RecipeHolder<?> holder,
                                      int mode, boolean hover) {
        List<ItemStack> inputs = inputsOf(holder);
        ItemStack result = resultOf(holder);
        switch (mode) {
            case MODE_FURNACE -> renderFurnace(gui, inputs, result, hover);
            case MODE_STONECUTTING, MODE_SMITHING -> renderFixedPair(gui, inputs, result, hover);
            default -> renderCrafting(gui, inputs, result);
        }
    }

    /** Crafting: 3x2 ingredient grid + result, at 2x scale (like 1.21.11
     *  renderGenericCrafting 的 vanilla 弹窗）。 */
    private static void renderCrafting(GuiGraphics gui, List<ItemStack> inputs, ItemStack result) {
        // Panel backdrop (56x56 dark panel)
        gui.fill(-4, -4, 52, 52, 0xE0000000);
        for (int i = 0; i < Math.min(inputs.size(), 6); i++) {
            int cx = i % 3;
            int cy = i / 3;
            scaledItem(gui, inputs.get(i), 2 + cx * 9, 2 + cy * 9);
        }
        if (!result.isEmpty()) {
            scaledItem(gui, result, 40, 2);
        }
    }

    /** Furnace: ingredient left, result right; 2x。 */
    private static void renderFurnace(GuiGraphics gui, List<ItemStack> inputs, ItemStack result, boolean hover) {
        gui.fill(-4, -4, 52, 52, 0xE0000000);
        scaledItem(gui, inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0), 2, 2);
        ClientCompat.blitSprite(gui, BRBTextures.FURNACE_FIRE_SPRITE, 6, 15, 6, 6);
        if (!result.isEmpty()) {
            scaledItem(gui, result, 38, 2);
        }
    }

    /** Stonecutter/smithing: input left, result right。 */
    private static void renderFixedPair(GuiGraphics gui, List<ItemStack> inputs, ItemStack result, boolean hover) {
        gui.fill(-4, -4, 52, 52, 0xE0000000);
        scaledItem(gui, inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0), 2, 2);
        if (!result.isEmpty()) {
            scaledItem(gui, result, 38, 2);
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

    private static List<ItemStack> inputsOf(RecipeHolder<?> holder) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (Ingredient ingredient : holder.value().getIngredients()) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) out.add(stacks[0]);
        }
        return out;
    }

    private static ItemStack resultOf(RecipeHolder<?> holder) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return ItemStack.EMPTY;
            return holder.value().getResultItem(mc.level.registryAccess());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // -- Mode constants (read from category id) ---------------------------------

    public static final int MODE_CRAFTING = 0;
    public static final int MODE_FURNACE = 1;
    public static final int MODE_STONECUTTING = 2;
    public static final int MODE_SMITHING = 3;

    /** Map a category to a popup render mode (fallback crafting). */
    public static int modeFor(String categoryId) {
        if (categoryId == null) return MODE_CRAFTING;
        return switch (categoryId) {
            case "furnace" -> MODE_FURNACE;
            case "stonecutting" -> MODE_STONECUTTING;
            case "smithing" -> MODE_SMITHING;
            default -> MODE_CRAFTING;
        };
    }

    // -- JEI delegated popup -----------------------------------------------------

    /** Render a JEI-backed entry's full JEI UI (delegated reflectively to the
     *  standalone headless-jei mod's {@code JeiPopupRenderer}).  Returns null
     *  when the mod is absent or the entry has no renderable layout (caller
     *  falls back to button highlight). */
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
}
