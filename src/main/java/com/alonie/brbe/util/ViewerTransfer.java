package com.alonie.brbe.util;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 查询界面的"转移物品"统一引擎（与 JEI 转移按钮同语义，纯客户端实现——执行走
 * 原版容器点击，服务器即权威）：
 *
 * <ul>
 *   <li><b>触发</b>：左键点击查询界面的配方对象；</li>
 *   <li><b>范围</b>：只转移<b>完全可合成</b>对象（rank 0）；残缺/不可合成对象
 *       <b>不转移、不补幽灵</b>；</li>
 *   <li><b>工作站匹配</b>：对象所属类别与<b>打开屏幕的菜单</b>匹配才转移，
 *       不匹配一概不转移（点击仅被消费）；</li>
 *   <li><b>执行</b>：缺失材料不转移（绝不报错/强行填充）；已有材料的槽位
 *       不覆盖；每次转移 = 客户端虚拟拖拽（PICKUP×2，原版服务器校验）。</li>
 * </ul>
 *
 * <p>自带配方书的菜单（合成台/熔炉系）走原版配方书放置（{@code tryPlaceRecipe}，
 * 服务器做网格映射）——熔炉对象<b>只转移材料</b>（燃料由玩家经燃料类别或手动
 * 放入）。固定布局站（石切/研磨/铁砧/锻造/酿造）按 display/布局槽位解析输入 →
 * 槽位分配表逐槽拖拽。
 */
public final class ViewerTransfer {

    private ViewerTransfer() {
    }

    /** 是否<b>完全可合成</b>（可合成且非残缺）——唯一允许转移的对象状态。 */
    public static boolean isFullyCraftable(RecipeCollection collection, RecipeDisplayId id) {
        return collection != null && id != null && collection.isCraftable(id)
                && !PartialCraftingUtil.isPartiallyCraftable(collection, id);
    }

    /**
     * 转移可合成对象 {@code id} 的材料到打开的容器。返回 true = 对象与打开的
     * 工作站匹配且已执行；false = 工作站不匹配/无槽位数据（调用方仍消费点击）。
     */
    public static boolean transfer(String categoryId, RecipeDisplayEntry entry,
                                   RecipeDisplayId id, RecipeCollection collection,
                                   AbstractContainerScreen<?> screen, boolean useMax) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;
        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null || entry == null) return false;
        if (categoryId.equals("crafting") && menu instanceof AbstractCraftingMenu) {
            return placeViaBook(id, collection, screen, useMax);
        }
        if (categoryId.equals("furnace") && menu instanceof AbstractFurnaceMenu) {
            // 熔炉对象只转移材料（原版配方书放置补输入槽）；燃料由玩家经
            // 燃料类别/手动放入——点击对象不自动补燃料。
            return placeViaBook(id, collection, screen, useMax);
        }
        List<SlotPlan> plan = slotPlan(categoryId, entry, menu);
        if (plan == null) return false;
        Map<Item, Integer> counts = PartialCraftingUtil.searchSpaceItemCounts();
        for (SlotPlan p : plan) {
            ItemStack owned = pickOwned(p.variants(), counts);
            if (owned.isEmpty()) continue; // 缺失：不转移也不幽灵
            Slot target = menu.getSlot(p.slot());
            if (!target.getItem().isEmpty()) {
                // 已含该物 → 完成；被其他物品占用 → 不强行替换。
                if (target.getItem().is(owned.getItem())) continue;
                continue;
            }
            moveStack(mc.gameMode, menu, owned, p.slot(), mc.player);
        }
        return true;
    }

    // ── 槽位规划（类别 → 菜单类型匹配 + 输入槽表）───────────────────────────

    /** 一项转移：菜单槽位索引 + 该槽可接受的物品变体。 */
    private record SlotPlan(int slot, List<ItemStack> variants) {
    }

    /** 类别 → 菜单类型 + 输入槽映射；类别与打开的菜单不匹配返回 null。 */
    private static List<SlotPlan> slotPlan(String categoryId, RecipeDisplayEntry entry,
                                           AbstractContainerMenu menu) {
        switch (categoryId) {
            case "stonecutting": {
                if (!(menu instanceof StonecutterMenu)) return null;
                var display = RecipeViewerIndex.asStonecutter(entry);
                List<ItemStack> variants = display == null ? List.of()
                        : RecipeViewerIndex.resolveSlotDisplay(display.input());
                if (variants.isEmpty()) {
                    List<SlotPlan> fallback = layoutSlots(entry, 1, false);
                    if (fallback != null && !fallback.isEmpty()) {
                        variants = fallback.get(0).variants();
                    }
                }
                return variants.isEmpty() ? null : List.of(new SlotPlan(0, variants));
            }
            case "smithing": {
                if (!(menu instanceof SmithingMenu)) return null;
                var display = RecipeViewerIndex.asSmithing(entry);
                if (display == null) return null;
                return List.of(
                        new SlotPlan(0, RecipeViewerIndex.resolveSlotDisplay(display.template())),
                        new SlotPlan(1, RecipeViewerIndex.resolveSlotDisplay(display.base())),
                        new SlotPlan(2, RecipeViewerIndex.resolveSlotDisplay(display.addition())));
            }
            case "grindstone":
                return menu instanceof GrindstoneMenu ? layoutSlots(entry, 2, false) : null;
            case "anvil":
                return menu instanceof AnvilMenu ? layoutSlots(entry, 2, false) : null;
            case "brewing":
                // 瓶槽(0-2)也转移：布局的 3 个瓶槽（最低行、按 x 排序）+ 材料槽
                //（y 最小者）→ 菜单 0,1,2,3。
                return menu instanceof BrewingStandMenu ? brewingSlots(entry) : null;
            default:
                // mod 工作站（无编译依赖，按菜单类型名匹配）：Farmer's Delight
                // 厨锅 = 6 个材料槽（菜单槽位 0-5），布局 role-0 输入槽供料。
                if (menu.getClass().getName().equals(
                        "vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu")) {
                    return layoutSlots(entry, 6, false);
                }
                return null;
        }
    }

    /** 布局槽位兜底（无 display 解析器的类别）：role 0 输入槽按 (x, y) 排序，
     *  取前 {@code count} 个映射到菜单 0..count-1。 */
    private static List<SlotPlan> layoutSlots(RecipeDisplayEntry entry, int count,
                                              boolean bottling) {
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(entry.id());
        if (layout == null) return null;
        List<RecipeViewerEngine.RecipeSlotLayout> inputs = new ArrayList<>();
        for (RecipeViewerEngine.RecipeSlotLayout s : layout.slots()) {
            if (s.role() == 0 && !s.stacks().isEmpty()) inputs.add(s);
        }
        if (inputs.isEmpty()) return null;
        inputs.sort(Comparator
                .comparingInt((RecipeViewerEngine.RecipeSlotLayout s) -> s.x())
                .thenComparingInt(s -> s.y()));
        List<SlotPlan> out = new ArrayList<>();
        for (int i = 0; i < Math.min(count, inputs.size()); i++) {
            out.add(new SlotPlan(i, inputs.get(i).stacks()));
        }
        return out.isEmpty() ? null : out;
    }

    /** 酿造：材料槽 = 布局中 y 最小者（JEI 顶部 (24,3)）；瓶槽 = 其余按 x 排序
     *  → 菜单 0,1,2 = 瓶，3 = 材料。 */
    private static List<SlotPlan> brewingSlots(RecipeDisplayEntry entry) {
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(entry.id());
        if (layout == null) return null;
        List<RecipeViewerEngine.RecipeSlotLayout> inputs = new ArrayList<>();
        for (RecipeViewerEngine.RecipeSlotLayout s : layout.slots()) {
            if (s.role() == 0 && !s.stacks().isEmpty()) inputs.add(s);
        }
        if (inputs.size() < 4) return null;
        inputs.sort(Comparator.comparingInt(s -> s.y()));
        List<RecipeViewerEngine.RecipeSlotLayout> bottles =
                new ArrayList<>(inputs.subList(1, inputs.size()));
        bottles.sort(Comparator.comparingInt(s -> s.x()));
        List<SlotPlan> out = new ArrayList<>();
        out.add(new SlotPlan(0, bottles.get(0).stacks()));
        out.add(new SlotPlan(1, bottles.get(1).stacks()));
        out.add(new SlotPlan(2, bottles.get(2).stacks()));
        out.add(new SlotPlan(3, inputs.get(0).stacks()));
        return out;
    }

    // ── 执行 ─────────────────────────────────────────────────────────────

    /** 从检索空间快照挑选第一个<b>拥有</b>的变体（拥有即视为该槽材料）。 */
    private static ItemStack pickOwned(List<ItemStack> variants, Map<Item, Integer> counts) {
        if (variants == null || variants.isEmpty() || counts == null || counts.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (ItemStack v : variants) {
            if (v != null && !v.isEmpty() && counts.containsKey(v.getItem())) return v;
        }
        return ItemStack.EMPTY;
    }

    /** 玩家物品栏中持有 {@code item} 的槽位，无则 -1。 */
    private static int inventorySlotWith(AbstractContainerMenu menu, ItemStack item) {
        for (int i = 3; i < menu.slots.size(); i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (!s.isEmpty() && s.is(item.getItem())) return i;
        }
        return -1;
    }

    /** 虚拟拖拽：收纳手持物品 → 拿起背包中的材料 → 放入目标槽 → 归位换出物。
     *  全部经原版容器点击（服务器校验 mayPickup/mayPlace）。 */
    private static void moveStack(MultiPlayerGameMode gameMode, AbstractContainerMenu menu,
                                  ItemStack item, int targetSlot, Player player) {
        int from = inventorySlotWith(menu, item);
        if (from < 0) return;
        if (!menu.getCarried().isEmpty()) {
            ClientInventoryUtil.storeItem(-1, i -> i >= 3);
        }
        gameMode.handleContainerInput(menu.containerId, from, 0, ContainerInput.PICKUP, player);
        gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            ClientInventoryUtil.storeItem(-1, i -> i >= 3);
        }
    }

    // ── 层1：原版配方书放置（合成台/熔炉——服务器做网格映射）───────────────

    private static boolean placeViaBook(RecipeDisplayId id, RecipeCollection collection,
                                        AbstractContainerScreen<?> screen, boolean useMax) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> rbs)) return false;
        RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) rbs)
                .brbe$getRecipeBookComponent();
        if (book == null || id == null || collection == null) return false;
        try {
            RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) book;
            boolean placed = accessor.tryPlaceRecipeInvoker(collection, id, useMax);
            if (!placed) {
                // 原版对"选择变化后的首次放置"会拒绝——重复点击路径保底
                //（完全可合成对象正常情况下直接成功）。
                accessor.setLastPlacedRecipe(id);
                accessor.tryPlaceRecipeInvoker(collection, id, useMax);
            }
            accessor.setLastRecipe(id);
            accessor.setLastRecipeCollection(collection);
            return true;
        } catch (Exception e) {
            // 非致命：放置已发出或无效。
            return false;
        }
    }
}
