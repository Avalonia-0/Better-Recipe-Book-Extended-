package com.alonie.brbe.util;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 容器槽位状态版本号 —— 自研配方书（酿造台 / 锻造台）集合缓存的失效判据。
 *
 * <h3>为什么需要它</h3>
 * 「这条配方材料齐不齐」只取决于两样东西：**槽位里的物品**与**鼠标上拿着的物品**。
 * 但自研书的同一个集合在一帧里会被反复询问：页面每格按钮问 5～6 次、管线分类每格问 3 次、
 * 组浮层再问几次，而每次询问原本都要把集合里全部配方 × 全部槽位重扫一遍
 * （{@code BRBSmithingRecipe#hasMaterials} 还会在每个槽位上复制一次基底 ItemStack）。
 * 锻造台的纹饰组一个集合 29 条配方（{@code #minecraft:trimmable_armor} 在 26.3 的 7+7+7+8
 * = 29 件装备）、共 18 个纹饰组，于是"打开配方书"变成几十万次槽位扫描 → 明显的打开卡顿。
 *
 * <p>这里给槽位内容算一个指纹；指纹一变就把版本号 +1。集合只在自己缓存的版本号过期时
 * 才重算一次，同一帧内的后续询问全部 O(1)。</p>
 *
 * <h3>指纹包含什么</h3>
 * 每个槽位：{@code ItemStack.hashItemAndComponents}（物品 + **全部数据组件**）与数量；
 * 最后再加上鼠标上的物品。**必须带上组件**：酿造台的输入判据用的是
 * {@code ItemStack.isSameItemSameComponents}（"药水"的物品本体都是 {@code minecraft:potion}，
 * 区别全在 {@code POTION_CONTENTS} 组件里；锻造台契约里 {@code TRIM} 组件也决定基底能否用），
 * 只看物品 id 会在"把 A 药水换成 B 药水"时漏判、缓存不失效。
 *
 * <p>只在客户端渲染线程使用，无需同步。</p>
 */
public final class RecipeSlotState {

    /** 版本号：槽位指纹每次变化 +1（跨菜单共用，只增不减）。 */
    private static long version;

    private static long lastFingerprint;
    private static boolean hasFingerprint;
    private static AbstractContainerMenu lastMenu;

    private RecipeSlotState() {
    }

    /**
     * 取当前槽位状态的版本号。指纹与上次相同（且菜单未变）时返回同一个版本号，
     * 调用方据此判断缓存是否仍然有效。
     */
    public static long version(AbstractContainerMenu menu) {
        if (menu == null) {
            return version;
        }

        long hash = 1125899906842597L;
        var slots = menu.slots;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            hash = hash * 31 + ItemStack.hashItemAndComponents(stack);
            hash = hash * 31 + stack.getCount();
        }

        ItemStack carried = menu.getCarried();
        hash = hash * 31 + ItemStack.hashItemAndComponents(carried);
        hash = hash * 31 + carried.getCount();

        if (!hasFingerprint || hash != lastFingerprint || menu != lastMenu) {
            lastFingerprint = hash;
            lastMenu = menu;
            hasFingerprint = true;
            version++;
        }
        return version;
    }
}
