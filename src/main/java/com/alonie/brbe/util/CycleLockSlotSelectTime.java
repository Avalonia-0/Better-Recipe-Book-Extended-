package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.SlotSelectTime;

/**
 * 原版配方书 {@link SlotSelectTime} 的锁定包装：按住「锁定折叠物品」键时返回
 * 冻结的变体下标，松开透传自动下标。
 *
 * <p>为什么包在这一层：原版 {@code RecipeBookComponent} 的构造器把**同一个**
 * {@code SlotSelectTime}（一个 {@code this::lambda$new$0} 方法引用）同时交给
 * {@code GhostSlots}（功能方块里的幽灵物品）与 {@code RecipeBookPage}（配方书
 * 网格按钮 / 替代配方浮层）——所以这一个包装点同时覆盖了这两处折叠物品
 * （用户 2026-09-13 的诉求 1）。</p>
 *
 * <p>返回值必须**非负**：原版消费方用的是普通取模（如
 * {@code RecipeButton.getDisplayStack} 的 {@code irem}、{@code GhostSlots} 的
 * 下标运算），负数会越界 —— 归一化在 {@link RecipeViewerOverlay#lockedCycleIndexNonNegative}
 * 里完成。</p>
 */
public final class CycleLockSlotSelectTime implements SlotSelectTime {

    private final SlotSelectTime delegate;

    public CycleLockSlotSelectTime(SlotSelectTime delegate) {
        this.delegate = delegate;
    }

    @Override
    public int currentIndex() {
        return RecipeViewerOverlay.lockedCycleIndexNonNegative(delegate.currentIndex());
    }
}
