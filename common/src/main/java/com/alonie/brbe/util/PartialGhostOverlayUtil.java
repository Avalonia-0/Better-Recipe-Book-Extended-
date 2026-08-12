package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.GhostRecipeAccessor;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 残缺配方幽灵物品的红遮罩增强。
 *
 * <p>合成台点击残缺配方后，原版 {@code GhostRecipe} 为每个材料槽位绘制红色背景
 * （0x30FF0000），表示"缺少材料"。本类在每次渲染幽灵物品前统计玩家物品栏各
 * 材料数量，按幽灵槽位从左到右、从上到下的顺序逐个扣除：库存中已有的材料，其
 * 对应槽位的红色遮罩被移除，让玩家一眼看出还剩哪些材料没凑齐。
 *
 * <p>渲染链路：{@code AbstractRecipeBookScreen.render} →
 * {@code RecipeBookComponent.renderGhostRecipe} → 本类 {@link #prepare}（经
 * {@code incompletecrafting.RecipeBookComponentMixin} 注入）→
 * {@code GhostRecipe.render} → {@link #shouldShowRedMask}（经
 * {@code incompletecrafting.GhostRecipeMixin} 重定向红色填充）。
 */
public final class PartialGhostOverlayUtil {
    private static boolean active;
    private static final Set<Long> noRedMaskSlots = new HashSet<>();

    private PartialGhostOverlayUtil() {
    }

    /**
     * 在渲染幽灵物品前调用，计算当前配方下应移除红色遮罩的槽位。
     * 完全可合成配方（材料齐全）跳过；残缺配方与不可合成配方都按数量扣除。
     *
     * @param recipe      当前幽灵配方（{@code GhostRecipe.getRecipe()}）
     * @param collection  配方所属集合（用于可合成判定）
     * @param menuSlots   容器槽位
     * @param carried     鼠标拿起物（计入库存）
     * @param ghostRecipe 幽灵配方实例
     * @param renderX     配方书原点 x（幽灵槽坐标是相对坐标）
     * @param renderY     配方书原点 y
     * @param bigSlot     结果槽是否大格子（{@code renderGhostRecipe} 的 boolean 参数）
     */
    public static void prepare(@Nullable RecipeHolder<?> recipe, RecipeCollection collection,
                               NonNullList<Slot> menuSlots, ItemStack carried,
                               GhostRecipe ghostRecipe, int renderX, int renderY, boolean bigSlot) {
        active = false;
        noRedMaskSlots.clear();
        if (recipe == null || collection == null || ghostRecipe == null) return;
        // 幽灵物品出现即代表放置失败（材料未齐），始终按 slots+carried 逐个扣除
        // 已有材料——完全可合成的配方放置成功不会渲染幽灵，故无需提前返回。
        // 不依赖 partialMarkingEnabled：用户关闭残缺配方标记后配方回到不可合成
        // 状态，幽灵物品红遮罩仍应正确指示缺失材料。

        // 物品栏各材料总数量（含鼠标拿起物），统计口径与 RecipeBookComponentMixin 一致。
        Map<Item, Integer> counts = new HashMap<>();
        for (Slot slot : menuSlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        if (!carried.isEmpty()) counts.merge(carried.getItem(), carried.getCount(), Integer::sum);

        // 幽灵槽位按渲染坐标 (y, x) 排序 = 从上到下、从左到右，即玩家感知的槽位顺序。
        List<GhostRecipe.GhostIngredient> ingredients =
                ((GhostRecipeAccessor) ghostRecipe).getIngredients();
        List<int[]> ordered = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            GhostRecipe.GhostIngredient ing = ingredients.get(i);
            // 结果槽（index 0 且大格子）不参与材料扣除，始终保留红遮罩。
            if (i == 0 && bigSlot) continue;
            ordered.add(new int[]{ing.getX() + renderX, ing.getY() + renderY, i});
        }
        ordered.sort(Comparator.comparingInt((int[] a) -> a[1]).thenComparingInt(a -> a[0]));

        // 逐个扣除：库存中还有该材料则移除该槽位红遮罩，并扣减剩余数量。
        active = true;
        for (int[] entry : ordered) {
            ItemStack stack = ingredients.get(entry[2]).getItem();
            if (stack == null || stack.isEmpty()) continue;
            Item item = stack.getItem();
            int available = counts.getOrDefault(item, 0);
            if (available > 0) {
                noRedMaskSlots.add(key(entry[0], entry[1]));
                counts.put(item, available - 1);
            }
        }
    }

    /**
     * {@code GhostRecipe.render} 绘制红色背景时调用。
     * true = 保留红遮罩；false = 跳过（该槽位材料已在物品栏中）。
     */
    public static boolean shouldShowRedMask(int x0, int y0) {
        if (!active) return true;
        if (noRedMaskSlots.contains(key(x0, y0))) return false;
        // 结果槽大格子分支以 (x0-4, y0-4) 传入，偏移回原坐标再匹配。
        if (noRedMaskSlots.contains(key(x0 + 4, y0 + 4))) return false;
        return true;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
