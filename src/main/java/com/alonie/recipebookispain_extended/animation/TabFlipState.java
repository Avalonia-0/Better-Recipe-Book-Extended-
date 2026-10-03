package com.alonie.recipebookispain_extended.animation;

import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;

/**
 * 单个 RBIP 标签按钮的「翻页伸展」状态（标签栏翻页动画用）。
 *
 * <p>每个 {@code RecipeBookTabButton} 自带一份（见
 * {@code RecipeGroupButtonMixin}），翻页动画只读写这份状态，从不改动标签的
 * 真实坐标 —— 标签在渲染时被临时挪到动画位置，画完立刻还原。</p>
 *
 * <p>语义：{@link #extend} 是「伸出度」，1 = 停在既定位置（静止态），0 = 完全缩进
 * 书体内部（被配方书盖住）。缩进方向由 {@link #placement} 决定（左列沿 +X、
 * 上排沿 +Y、下排沿 -Y，只走单轴）。</p>
 */
public final class TabFlipState {

    /** 伸出度：1 = 完全伸出（静止位），0 = 完全缩进书体内部。 */
    public float extend = 1.0F;

    /** 目标伸出度：1 = 本页标签（要伸出），0 = 退场标签（要缩进）。 */
    public float target = 1.0F;

    /** 是否参与本轮翻页动画。false = 完全走原版渲染路径（搜索标签恒为 false）。 */
    public boolean tracked;


    /** 静止位 = {@link #extend} 为 1 时的位置（缩进的起点/终点）。 */
    public int baseX;
    public int baseY;

    /** 静止位的朝向 —— 同时决定缩进方向与裁剪边界在哪一侧。 */
    public RecipeGroupButtonPlacement placement = RecipeGroupButtonPlacement.NORMAL;

    /** 记入「以当前位置为静止位、尚未参与过动画」的初态。 */
    public void resetAt(int x, int y, RecipeGroupButtonPlacement placement) {
        this.baseX = x;
        this.baseY = y;
        this.placement = placement;
    }
}
