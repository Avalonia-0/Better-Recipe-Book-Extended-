package com.alonie.brbe.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 点击配方放置时，把鼠标拿起物（carried）作为"特殊槽位"直接参与合成格摆放。
 *
 * <p>背景：服务端放置（{@code ServerPlaceRecipe.placeRecipe}）<b>无条件
 * {@code clearGrid}</b> 且只从物品栏取料重填（反编译确认）——carried 不在服务端
 * 可见范围，料必须先放进物品栏才会被服务端用到。这就是旧 topUp 方案"材料经过
 * 物品栏"的根因，也是物品栏满时失败的原因。</p>
 *
 * <p>本方案<b>取消服务端放置包</b>，客户端把 carried 料直接摆进合成格对应槽位：
 * 网格摆好后结果槽自动出产物（服务端 {@code CraftingMenu.slotsChanged} 计算）。
 * carried 料永不经过物品栏，物品栏满时也能摆放，且<b>从不被放回物品栏</b>。</p>
 *
 * <p><b>carried 剩余的处理（空槽暂存）</b>：carried 只能持一种材料，从物品栏取
 * 不匹配材料必须经 carried 中转，故 carried 必须腾空。carried 用完匹配位置后若有
 * 剩余，<b>暂存进配方区域外的网格空槽</b>（button=0 整堆放入），腾空 carried 从
 * 物品栏补剩余位置，最后 button=0 拿回暂存料——carried 全程留在手里（或进网格），
 * 不被没收。配方占满网格无空槽时，走原版服务端放置兜底（carried 保持不动）。</p>
 *
 * <p>触发条件：carried 非空 + 配方是合成配方
 * （{@link ShapedCraftingRecipeDisplay} / {@link ShapelessCraftingRecipeDisplay}）。
 * 任意环节失败（清网格失败、料不足、布局无法解析）→ 返回 false，调用方走原版
 * 服务端放置兜底——服务端 clearGrid 会纠正网格，料不丢失。</p>
 */
public final class CarriedPlaceHelper {

    private static final class Placement {
        final Slot slot;
        final Set<Item> candidates;
        final boolean outside; // 配方区域外（暂存优先用）

        Placement(Slot slot, Set<Item> candidates, boolean outside) {
            this.slot = slot;
            this.candidates = candidates;
            this.outside = outside;
        }

        boolean isNeeded() {
            return !candidates.isEmpty();
        }
    }

    private CarriedPlaceHelper() {
    }

    /**
     * 主入口：把 carried 料摆进合成格。成功返回 true（调用方应 cancel 服务端放置包）。
     *
     * @param gridWidth 合成网格宽度（用于 shaped 配方左上对齐映射）
     */
    public static boolean placeGridFromCarried(int containerId, RecipeDisplayId recipeId,
                                               List<RecipeDisplayEntry> entries,
                                               List<Slot> gridSlots, int gridWidth,
                                               int resultSlotIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.level == null) return false;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.containerId != containerId) return false;
        if (menu.getCarried().isEmpty()) return false;

        RecipeDisplayEntry entry = findEntry(entries, recipeId);
        if (entry == null) return false;
        RecipeDisplay display = entry.display();
        if (!(display instanceof ShapedCraftingRecipeDisplay || display instanceof ShapelessCraftingRecipeDisplay)) {
            return false;
        }

        ContextMap ctx = SlotDisplayContext.fromLevel(mc.level);

        // 1) 解析布局：每个网格槽的候选材料集合（shaped 左上对齐 / shapeless 前 N 槽）
        List<Placement> placements = parsePlacements(display, gridSlots, gridWidth, ctx);
        if (placements == null) return false;

        // 2) 清网格：旧料收进 carried（同类合并）或 QUICK_MOVE 回物品栏；失败走原版
        if (!clearGridToCarried(menu, gridSlots, mc.gameMode, mc.player)) return false;

        // 3) 预检：carried + 物品栏能否填满所有需要位置（含暂存空槽判断）
        if (!canFulfill(menu, placements)) return false;

        // 4) 摆网格；中途失败走原版兜底
        return placeGrid(menu, placements, mc.gameMode, mc.player);
    }

    private static RecipeDisplayEntry findEntry(List<RecipeDisplayEntry> entries, RecipeDisplayId id) {
        for (RecipeDisplayEntry e : entries) {
            if (e.id().equals(id)) return e;
        }
        return null;
    }

    private static List<Placement> parsePlacements(RecipeDisplay display, List<Slot> gridSlots,
                                                   int gridWidth, ContextMap ctx) {
        List<Placement> out = new ArrayList<>(gridSlots.size());
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            int dw = shaped.width();
            int dh = shaped.height();
            List<SlotDisplay> ings = shaped.ingredients();
            for (int i = 0; i < gridSlots.size(); i++) {
                int r = i / gridWidth;
                int c = i % gridWidth;
                boolean outside = r >= dh || c >= dw;
                Set<Item> cand;
                if (!outside) {
                    SlotDisplay sd = ings.get(r * dw + c);
                    if (sd instanceof SlotDisplay.Empty) {
                        cand = Set.of();
                    } else {
                        cand = resolveCandidates(sd, ctx);
                        if (cand.isEmpty()) return null; // 复杂 SlotDisplay 无法解析 → 走原版
                    }
                } else {
                    cand = Set.of(); // 网格超出配方区域
                }
                out.add(new Placement(gridSlots.get(i), cand, outside));
            }
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            List<SlotDisplay> ings = shapeless.ingredients();
            for (int i = 0; i < gridSlots.size(); i++) {
                if (i < ings.size()) {
                    SlotDisplay sd = ings.get(i);
                    Set<Item> cand = resolveCandidates(sd, ctx);
                    if (cand.isEmpty()) return null;
                    out.add(new Placement(gridSlots.get(i), cand, false));
                } else {
                    out.add(new Placement(gridSlots.get(i), Set.of(), true));
                }
            }
        } else {
            return null;
        }
        return out;
    }

    private static Set<Item> resolveCandidates(SlotDisplay sd, ContextMap ctx) {
        Set<Item> set = new HashSet<>();
        for (ItemStack s : sd.resolveForStacks(ctx)) {
            if (!s.isEmpty()) set.add(s.getItem());
        }
        return set;
    }

    /**
     * 清网格：旧料收进 carried（同类两阶段合并 / 空手拿起），不同类或超容量走
     * QUICK_MOVE 回物品栏。物品栏放不下（QUICK_MOVE 无效）→ 返回 false 走原版。
     */
    private static boolean clearGridToCarried(AbstractContainerMenu menu, List<Slot> gridSlots,
                                              MultiPlayerGameMode gameMode, Player player) {
        int containerId = menu.containerId;
        for (Slot slot : gridSlots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) {
                // 空手拿起整堆
                gameMode.handleInventoryMouseClick(containerId, slot.index, 0, ClickType.PICKUP, player);
                continue;
            }
            if (ItemStack.isSameItemSameComponents(carried, stack)
                    && (long) carried.getCount() + stack.getCount() <= carried.getMaxStackSize()) {
                // 两阶段：carried 倒进槽合并 → 空 carried 拿起整堆
                gameMode.handleInventoryMouseClick(containerId, slot.index, 0, ClickType.PICKUP, player);
                if (!menu.getCarried().isEmpty()) return false;
                gameMode.handleInventoryMouseClick(containerId, slot.index, 0, ClickType.PICKUP, player);
                continue;
            }
            // 不同类 / 超容量：QUICK_MOVE 回物品栏；物品栏满移不干净 → fallback 原版
            gameMode.handleInventoryMouseClick(containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
            if (!slot.getItem().isEmpty()) return false; // 残留会破坏新配方形状
        }
        return true;
    }

    /**
     * 预检：模拟 {@link #placeGrid} 的三阶段，确认清网格后的 carried + 物品栏能填满
     * 所有需要位置。
     *
     * <p>carried 优先填匹配位置（阶段 A，逐位置消耗 1 个）；carried 用尽后剩余位置
     * 由物品栏补（carried 空，可安全取料）。carried 有剩余时：若已摆满 → 成功（剩余
     * 保持 carried）；若还有位置要摆 → 需<b>空槽暂存</b>腾空 carried，无空槽则 fallback。
     * carried 剩余<b>不要求</b>物品栏有空间（暂存到网格，不进物品栏）。</p>
     */
    private static boolean canFulfill(AbstractContainerMenu menu, List<Placement> placements) {
        ItemStack carried = menu.getCarried();
        int remaining = carried.getCount();
        Item carriedItem = carried.getItem();
        Map<Item, Integer> inv = countInventory(menu);

        boolean[] phaseAPlaced = new boolean[placements.size()];
        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            if (!p.isNeeded() || remaining <= 0) continue;
            if (p.candidates.contains(carriedItem)) {
                remaining--;
                phaseAPlaced[i] = true;
            }
        }

        // 剩余需要摆的位置
        int pendingCount = 0;
        for (int i = 0; i < placements.size(); i++) {
            if (placements.get(i).isNeeded() && !phaseAPlaced[i]) pendingCount++;
        }
        if (pendingCount == 0) return true; // 已摆满，carried 剩余保持

        // carried 有剩余且还需摆料 → 必须空槽暂存腾空 carried
        if (remaining > 0 && findEmptySlot(placements) == null) return false;

        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            if (p.isNeeded() && !phaseAPlaced[i]) {
                if (!consumeFromMap(inv, p.candidates)) return false;
            }
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
     * 摆网格：<b>阶段 A</b> carried 匹配的位置逐个分 1 个（button=1）；carried 用尽后
     * 剩余位置由物品栏两阶段补；<b>carried 有剩余</b>时先暂存进空槽腾空、补料后再拿回。
     * 中途失败返回 false（走原版兜底）。
     */
    private static boolean placeGrid(AbstractContainerMenu menu, List<Placement> placements,
                                     MultiPlayerGameMode gameMode, Player player) {
        int containerId = menu.containerId;

        // 阶段 A：carried 匹配的位置
        for (Placement p : placements) {
            if (!p.isNeeded()) continue;
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            if (p.candidates.contains(carried.getItem())) {
                gameMode.handleInventoryMouseClick(containerId, p.slot.index, 1, ClickType.PICKUP, player);
            }
        }

        // carried 剩余：暂存进空槽腾空，供物品栏补料
        Slot stash = null;
        if (!menu.getCarried().isEmpty()) {
            stash = findEmptySlot(placements);
            if (stash == null) return false;
            gameMode.handleInventoryMouseClick(containerId, stash.index, 0, ClickType.PICKUP, player);
            if (!menu.getCarried().isEmpty()) return false; // 放不下
        }

        // 阶段 C：其余需要位置从物品栏补料
        boolean filled = fillRemainingFromInventory(menu, placements, gameMode, player);

        // 拿回暂存料（carried 恢复）
        if (stash != null && menu.getCarried().isEmpty()) {
            gameMode.handleInventoryMouseClick(containerId, stash.index, 0, ClickType.PICKUP, player);
        }
        return filled;
    }

    private static boolean fillRemainingFromInventory(AbstractContainerMenu menu, List<Placement> placements,
                                                      MultiPlayerGameMode gameMode, Player player) {
        int containerId = menu.containerId;
        for (Placement p : placements) {
            if (!p.isNeeded()) continue;
            if (!p.slot.getItem().isEmpty()) continue; // 阶段 A 已摆
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                // 理论上 carried 已用尽或暂存；若仍匹配继续用，否则无法安全取料
                if (p.candidates.contains(carried.getItem())) {
                    gameMode.handleInventoryMouseClick(containerId, p.slot.index, 1, ClickType.PICKUP, player);
                    continue;
                }
                return false;
            }
            Slot inv = findInventorySlot(menu, p.candidates);
            if (inv == null) return false;
            gameMode.handleInventoryMouseClick(containerId, inv.index, 0, ClickType.PICKUP, player);
            gameMode.handleInventoryMouseClick(containerId, p.slot.index, 1, ClickType.PICKUP, player);
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

    /**
     * 找空槽暂存 carried 剩余：优先配方区域外（不破坏配方形状，结果槽不闪烁），
     * 其次配方区域内空位。槽必须为空（清网格后）。
     */
    private static Slot findEmptySlot(List<Placement> placements) {
        for (Placement p : placements) {
            if (!p.isNeeded() && p.outside && p.slot.getItem().isEmpty()) return p.slot;
        }
        for (Placement p : placements) {
            if (!p.isNeeded() && p.slot.getItem().isEmpty()) return p.slot;
        }
        return null;
    }
}
