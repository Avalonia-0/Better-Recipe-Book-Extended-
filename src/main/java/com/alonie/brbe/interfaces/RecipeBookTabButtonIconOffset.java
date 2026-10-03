package com.alonie.brbe.interfaces;

/** 配方书标签图标的纵向微调：首个可见标签 -1px、末个可见标签 +1px
 *  （见 {@code RecipeBookComponentTabIconOffsetMixin}）。RBIP 的标签自绘路径
 *  （选中态渐变 / 旋转条带）绕过了原版 {@code extractIcon}，必须自己把这个偏移带上，
 *  否则过渡动画期间图标会相对静止态跳 1px（用户 2026-10-03 反馈搜索标签的情况）。 */
public interface RecipeBookTabButtonIconOffset {
    void brbe$setIconYOffset(int iconYOffset);

    int brbe$getIconYOffset();
}
