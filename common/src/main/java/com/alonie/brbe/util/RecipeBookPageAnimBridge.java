package com.alonie.brbe.util;

/**
 * 合成台配方书翻页动画的"用户主动翻页"标记桥。
 *
 * <p>{@code RecipeBookPage.updateButtonsForPage} 是所有翻页/刷新/恢复页码的汇聚点，
 * 动画 mixin 无法仅凭页码变化区分"用户翻页"与"程序恢复（保存浏览记录）"。
 * 用户翻页入口（箭头点击、滚轮、scroll-around 回绕）先调用 {@link #markUserFlip()}，
 * 动画 mixin 在 {@code updateButtonsForPage} 开头消费该标记，只有用户翻页才启动动画。</p>
 */
public final class RecipeBookPageAnimBridge {

    private static boolean userFlip;

    private RecipeBookPageAnimBridge() {
    }

    public static void markUserFlip() {
        userFlip = true;
    }

    public static boolean consumeUserFlip() {
        boolean b = userFlip;
        userFlip = false;
        return b;
    }
}
