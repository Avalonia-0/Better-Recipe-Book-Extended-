package com.alonie.brbe.pinoverlay;

/**
 * 临时覆盖配方按钮悬停渲染（pin 克隆按钮冻结缩放/布局模式）。
 *
 * <p>1.21.1 版：pin 渲染直接用 PopupRenderer（无克隆 OverlayRecipeButton——
 * 1.21.1 的 OverlayRecipeComponent 无 SlotSelectTime），本类保留为布局模式
 * 单一来源（与 1.21.11 对齐，PinOverlay.render 的 push/pop 上下文）。</p>
 */
public final class PinButtonRenderOverride {

    public static final int MODE_CRAFTING = 0;
    public static final int MODE_FURNACE = 1;
    public static final int MODE_STONECUTTING = 2;
    public static final int MODE_SMITHING = 3;
    public static final int MODE_ANVIL = 4;
    public static final int MODE_BREWING = 5;
    public static final int MODE_GRINDSTONE = 6;

    public static final float VANILLA_SCALE = 2f;

    private static float current = -1f;
    private static int mode = -1;

    private PinButtonRenderOverride() {}

    public static void push(float scale, int pinMode) {
        current = scale;
        mode = pinMode;
    }

    public static void pop() {
        current = -1f;
        mode = -1;
    }

    public static boolean active() {
        return current >= 0f;
    }

    public static float current() {
        return current;
    }

    public static boolean isFurnace() {
        return mode == MODE_FURNACE;
    }

    public static boolean isStonecutting() {
        return mode == MODE_STONECUTTING;
    }

    public static boolean isSmithing() {
        return mode == MODE_SMITHING;
    }
}
