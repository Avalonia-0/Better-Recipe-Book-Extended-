package com.alonie.brbe.mixins.cyclelock;

/**
 * 幽灵物品所属 {@code GhostRecipe} 暴露的**渲染原点**（容器左上角）。
 *
 * <p>1.21.1 的幽灵物品位置是「容器相对坐标」（{@code GhostIngredient.getX/Y} 来自
 * {@code Slot.x/y}），真正的屏幕坐标 = 渲染原点 + 它 —— 原点只在
 * {@code GhostRecipe.render(gui, mc, x, y, …)} 的参数里出现，所以由
 * {@link GhostRecipeCycleLockMixin} 记下来，供 {@link GhostIngredientCycleLockMixin}
 * 做逐物品的指针命中判定（用户 2026-09-13 诉求 2）。</p>
 */
public interface GhostRecipeOrigin {

    int brbe$originX();

    int brbe$originY();
}
