package com.alonie.recipebookispain_extended.access;

import com.alonie.recipebookispain_extended.animation.TabFlipState;

/**
 * {@code RecipeBookTabButton} 的「翻页伸展状态」访问口（标签栏翻页动画用）。
 *
 * <p>状态挂在按钮自己身上而不是组件的一张表里：{@code initVisuals()} 会整批重建
 * 标签按钮，随按钮一起生灭的状态天然不会残留。</p>
 */
public interface RecipeGroupButtonFlipAccess {

    TabFlipState rbip$flipState();

    /**
     * 标签「新登场」（本次翻页从书体内部伸出）时调用：把选中态渐变**一并归零**。
     *
     * <p>必须做：被"瞬间隐藏"的标签（点标签触发跟随翻页 / 搜索重排 / 重开配方书 —— 这些都不走
     * 翻页动画、不进 {@code min()} 分支）其 {@code blend} 会冻在 1；不归零的话它再登场时
     * {@code min(1, extend)} 会让选中态跟着伸出一起涨满，落地时已经是 1 —— 看起来就是
     * "静止瞬间闪到书皮之上"（用户 2026-10-04 反馈）。</p>
     */
    void rbip$beginFlipIn();

    /**
     * 该标签此刻是否处于「停靠渐显」窗口 —— **只有登场且选中**的标签会返回 true
     * （其余标签一律整块被书皮压住）。组件据此决定要不要跳过"整块裁到书皮边缘"的外层
     * scissor：跳过之后由按钮自己拆成「本体裁住 + 固定的停靠窗口渐显」。
     */
    boolean rbip$revealsDock();
}
