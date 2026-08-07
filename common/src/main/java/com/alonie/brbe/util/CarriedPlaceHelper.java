package com.alonie.brbe.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.recipebook.PlaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 点击配方放置时，把鼠标拿起物（carried）作为"特殊槽位"直接参与合成格摆放（1.21.1 版）。
 *
 * <p>与 26.x 版同设计：服务端放置（{@code ServerPlaceRecipe.placeRecipe}）无条件
 * clearGrid 且只从物品栏取料，carried 不在服务端可见范围。本方案取消服务端放置包，
 * 客户端把 carried 料直接摆进合成格；carried 剩余暂存进网格空槽，<b>永不进物品栏</b>。</p>
 *
 * <p>1.21.1 差异：配方以 {@link RecipeHolder} 传递，布局用 vanilla
 * {@link PlaceRecipe#placeRecipe} 默认方法（{@link LayoutCollector} 收集每个材料对应的
 * 绝对槽索引），点击用 {@link ClickType}（{@code handleInventoryMouseClick}）。网格槽为
 * 槽索引 {@code 1..gridWidth*gridHeight}（CraftingMenu 与 InventoryMenu 通用）。</p>
 *
 * <p>任意环节失败 → 返回 false，调用方走原版服务端放置兜底（carried 保持不动，料不丢）。</p>
 */
public final class CarriedPlaceHelper {

    /**
     * 收集 vanilla {@link PlaceRecipe#placeRecipe} 映射出的「绝对槽索引 → 材料」。
     * addItemToSlot 的 slotIndex 是 menu.slots 的绝对索引（与 RecipeBookComponent 一致）。
     */
    private static final class LayoutCollector implements PlaceRecipe<Ingredient> {
        final Map<Integer, Ingredient> slotToIngredient = new HashMap<>();

        @Override
        public void addItemToSlot(Ingredient ingredient, int slotIndex, int x, int y, int slotOffset) {
            if (!ingredient.isEmpty()) {
                slotToIngredient.put(slotIndex, ingredient);
            }
        }
    }

    private CarriedPlaceHelper() {
    }

    /**
     * 主入口：把 carried 料摆进合成格。成功返回 true（调用方应 cancel 服务端放置包）。
     *
     * @param gridWidth  / gridHeight 合成网格尺寸（RecipeBookMenu.getGridWidth/Height）
     * @param resultSlotIndex 结果槽索引（CraftingMenu/InventoryMenu.getResultSlotIndex）
     */
    public static boolean placeGridFromCarried(int containerId, ResourceLocation recipeId,
                                               List<RecipeHolder<?>> holders,
                                               AbstractContainerMenu menu,
                                               int gridWidth, int gridHeight, int resultSlotIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;
        if (menu == null || menu.containerId != containerId) return false;
        if (menu.getCarried().isEmpty()) return false;

        RecipeHolder<?> holder = findHolder(holders, recipeId);
        if (holder == null) return false;
        Recipe<?> recipe = holder.value();
        if (!(recipe instanceof CraftingRecipe)) return false;

        // 1) 布局：用 vanilla PlaceRecipe 映射每个材料 → 绝对槽索引 → 候选 Item 集合
        Map<Integer, Set<Item>> layout = collectLayout(gridWidth, gridHeight, resultSlotIndex, holder);
        if (layout == null || layout.isEmpty()) return false;

        // 网格槽索引：1..gridWidth*gridHeight（CraftingMenu/InventoryMenu 均从 1 开始）
        List<Integer> gridIndices = new ArrayList<>();
        for (int i = 1; i <= gridWidth * gridHeight; i++) gridIndices.add(i);

        // 2) 清网格：旧料收进 carried（同类合并）或 QUICK_MOVE 回物品栏；失败走原版
        if (!clearGridToCarried(menu, gridIndices, mc.gameMode, mc.player)) return false;

        // 3) 预检：carried + 物品栏能否填满所有需要位置（含暂存空槽判断）
        if (!canFulfill(menu, layout, gridIndices)) return false;

        // 4) 摆网格；中途失败走原版兜底
        return placeGrid(menu, layout, gridIndices, mc.gameMode, mc.player);
    }

    private static RecipeHolder<?> findHolder(List<RecipeHolder<?>> holders, ResourceLocation id) {
        for (RecipeHolder<?> h : holders) {
            if (h.id().equals(id)) return h;
        }
        return null;
    }

    private static Map<Integer, Set<Item>> collectLayout(int gridWidth, int gridHeight, int resultSlotIndex,
                                                         RecipeHolder<?> holder) {
        LayoutCollector collector = new LayoutCollector();
        collector.placeRecipe(gridWidth, gridHeight, resultSlotIndex, holder,
                holder.value().getIngredients().iterator(), 0);
        Map<Integer, Set<Item>> out = new HashMap<>();
        for (Map.Entry<Integer, Ingredient> e : collector.slotToIngredient.entrySet()) {
            Set<Item> cand = resolveCandidates(e.getValue());
            if (cand.isEmpty()) return null; // 复杂 Ingredient 无法解析 → 走原版
            out.put(e.getKey(), cand);
        }
        return out;
    }

    private static Set<Item> resolveCandidates(Ingredient ingredient) {
        Set<Item> set = new HashSet<>();
        for (ItemStack s : ingredient.getItems()) {
            if (!s.isEmpty()) set.add(s.getItem());
        }
        return set;
    }

    /**
     * 清网格：旧料收进 carried（同类两阶段合并 / 空手拿起），不同类或超容量走
     * QUICK_MOVE 回物品栏。物品栏放不下（QUICK_MOVE 无效）→ 返回 false 走原版。
     */
    private static boolean clearGridToCarried(AbstractContainerMenu menu, List<Integer> gridIndices,
                                              MultiPlayerGameMode gameMode, Player player) {
        int containerId = menu.containerId;
        for (int idx : gridIndices) {
            Slot slot = menu.slots.get(idx);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) {
                gameMode.handleInventoryMouseClick(containerId, idx, 0, ClickType.PICKUP, player);
                continue;
            }
            if (ItemStack.isSameItemSameComponents(carried, stack)
                    && (long) carried.getCount() + stack.getCount() <= carried.getMaxStackSize()) {
                gameMode.handleInventoryMouseClick(containerId, idx, 0, ClickType.PICKUP, player);
                if (!menu.getCarried().isEmpty()) return false;
                gameMode.handleInventoryMouseClick(containerId, idx, 0, ClickType.PICKUP, player);
                continue;
            }
            // 不同类 / 超容量：QUICK_MOVE 回物品栏；物品栏满移不干净 → fallback 原版
            gameMode.handleInventoryMouseClick(containerId, idx, 0, ClickType.QUICK_MOVE, player);
            if (!slot.getItem().isEmpty()) return false; // 残留会破坏新配方形状
        }
        return true;
    }

    /**
     * 预检：模拟 {@link #placeGrid} 的三阶段，确认清网格后的 carried + 物品栏能填满
     * 所有需要位置。carried 有剩余且还需摆料时，必须有空槽暂存，否则 fallback。
     */
    private static boolean canFulfill(AbstractContainerMenu menu, Map<Integer, Set<Item>> layout,
                                      List<Integer> gridIndices) {
        ItemStack carried = menu.getCarried();
        int remaining = carried.getCount();
        Item carriedItem = carried.getItem();
        Map<Item, Integer> inv = countInventory(menu);

        // 阶段 A：carried 匹配的位置
        Set<Integer> phaseAPlaced = new HashSet<>();
        for (Map.Entry<Integer, Set<Item>> e : layout.entrySet()) {
            if (remaining <= 0) break;
            if (e.getValue().contains(carriedItem)) {
                remaining--;
                phaseAPlaced.add(e.getKey());
            }
        }

        // 剩余需要摆的位置
        List<Integer> pending = new ArrayList<>();
        for (Integer idx : layout.keySet()) {
            if (!phaseAPlaced.contains(idx)) pending.add(idx);
        }
        if (pending.isEmpty()) return true; // 已摆满，carried 剩余保持

        // carried 有剩余且还需摆料 → 必须空槽暂存腾空 carried
        if (remaining > 0 && findEmptySlot(layout, gridIndices, menu) == null) return false;

        for (Integer idx : pending) {
            if (!consumeFromMap(inv, layout.get(idx))) return false;
        }
        return true;
    }

    private static Map<Item, Integer> countInventory(AbstractContainerMenu menu) {
        Map<Item, Integer> map = new HashMap<>();
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) continue;
            ItemStack st = s.getItem();
            if (!st.isEmpty()) map.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        return map;
    }

    private static boolean consumeFromMap(Map<Item, Integer> map, Set<Item> candidates) {
        for (Item it : candidates) {
            Integer c = map.get(it);
            if (c != null && c > 0) {
                map.put(it, c - 1);
                return true;
            }
        }
        return false;
    }

    /**
     * 摆网格：阶段 A carried 匹配位置分 1 个；carried 剩余暂存空槽腾空；阶段 C 从物品栏
     * 补剩余位置；最后拿回暂存料。
     */
    private static boolean placeGrid(AbstractContainerMenu menu, Map<Integer, Set<Item>> layout,
                                     List<Integer> gridIndices,
                                     MultiPlayerGameMode gameMode, Player player) {
        int containerId = menu.containerId;

        // 阶段 A：carried 匹配的位置
        for (Map.Entry<Integer, Set<Item>> e : layout.entrySet()) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            if (e.getValue().contains(carried.getItem())) {
                gameMode.handleInventoryMouseClick(containerId, e.getKey(), 1, ClickType.PICKUP, player);
            }
        }

        // carried 剩余：暂存进空槽腾空，供物品栏补料
        Slot stash = null;
        if (!menu.getCarried().isEmpty()) {
            stash = findEmptySlot(layout, gridIndices, menu);
            if (stash == null) return false;
            gameMode.handleInventoryMouseClick(containerId, stash.index, 0, ClickType.PICKUP, player);
            if (!menu.getCarried().isEmpty()) return false; // 放不下
        }

        // 阶段 C：其余需要位置从物品栏补料
        boolean filled = fillRemainingFromInventory(menu, layout, gameMode, player);

        // 拿回暂存料（carried 恢复）
        if (stash != null && menu.getCarried().isEmpty()) {
            gameMode.handleInventoryMouseClick(containerId, stash.index, 0, ClickType.PICKUP, player);
        }
        return filled;
    }

    private static boolean fillRemainingFromInventory(AbstractContainerMenu menu, Map<Integer, Set<Item>> layout,
                                                      MultiPlayerGameMode gameMode, Player player) {
        int containerId = menu.containerId;
        for (Map.Entry<Integer, Set<Item>> e : layout.entrySet()) {
            int idx = e.getKey();
            if (!menu.slots.get(idx).getItem().isEmpty()) continue; // 阶段 A 已摆
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                if (e.getValue().contains(carried.getItem())) {
                    gameMode.handleInventoryMouseClick(containerId, idx, 1, ClickType.PICKUP, player);
                    continue;
                }
                return false;
            }
            Slot inv = findInventorySlot(menu, e.getValue());
            if (inv == null) return false;
            gameMode.handleInventoryMouseClick(containerId, inv.index, 0, ClickType.PICKUP, player);
            gameMode.handleInventoryMouseClick(containerId, idx, 1, ClickType.PICKUP, player);
            if (!menu.getCarried().isEmpty()) {
                gameMode.handleInventoryMouseClick(containerId, inv.index, 0, ClickType.PICKUP, player);
            }
        }
        return true;
    }

    private static Slot findInventorySlot(AbstractContainerMenu menu, Set<Item> candidates) {
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) continue;
            ItemStack st = s.getItem();
            if (!st.isEmpty() && candidates.contains(st.getItem())) return s;
        }
        return null;
    }

    /** 找空槽暂存 carried 剩余：网格中未被配方占用的槽（清网格后为空）。 */
    private static Slot findEmptySlot(Map<Integer, Set<Item>> layout, List<Integer> gridIndices,
                                      AbstractContainerMenu menu) {
        for (int idx : gridIndices) {
            if (!layout.containsKey(idx) && menu.slots.get(idx).getItem().isEmpty()) {
                return menu.slots.get(idx);
            }
        }
        return null;
    }
}
