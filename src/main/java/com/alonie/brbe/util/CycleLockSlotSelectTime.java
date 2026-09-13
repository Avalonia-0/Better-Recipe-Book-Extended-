package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.SlotSelectTime;

/**
 * 原版配方书 {@link SlotSelectTime} 的包装：把取值交给 {@link CycleLock} 的
 * **逐物品**判定——指针下那一件折叠物品返回冻结下标（滚轮可翻动），其余透传
 * 自动下标（用户 2026-09-13 诉求 2：锁定键只作用于鼠标指向的物品）。
 *
 * <p>为什么包在这一层：原版 {@code RecipeBookComponent} 的构造器把**同一个**
 * {@code SlotSelectTime}（一个 {@code this::lambda$new$0} 方法引用）交给
 * {@code GhostSlots}（功能方块里的幽灵物品）与 {@code RecipeBookPage}（配方书
 * 网格按钮 / 替代配方浮层）——共享实例意味着"现在画的是哪一件"只能由调用方
 * 说明，所以配方书前端在取值前用 {@link CycleLock#pushContext} 压入正在绘制的
 * 物品（网格按钮 = {@code RecipeButton.getDisplayStack/getCurrentRecipe}，
 * 幽灵物品 = {@code GhostSlots} 的逐槽绘制/提示），这里再
 * {@link CycleLock#resolveContext} 就地判定。</p>
 *
 * <p>返回值必须**非负**：原版消费方用的是普通取模（如
 * {@code RecipeButton.getDisplayStack} 的 {@code irem}、{@code GhostSlots} 的
 * 下标运算），负数会越界 —— {@link CycleLock#indexFor} 已经夹在 0 以上。</p>
 */
public final class CycleLockSlotSelectTime implements SlotSelectTime {

    private final SlotSelectTime delegate;

    public CycleLockSlotSelectTime(SlotSelectTime delegate) {
        this.delegate = delegate;
    }

    @Override
    public int currentIndex() {
        return CycleLock.resolveContext(delegate.currentIndex());
    }
}
