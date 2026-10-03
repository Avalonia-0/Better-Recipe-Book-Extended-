package com.alonie.brbe.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Simple utility to handle client side movement of items
 * @author Tau
 */
public class ClientInventoryUtil {

    /**
     * Stores an item from a slot/cursor to a slot within the bounds of indexCheck<br>
     * Note: If there is an item being carried bny the cursor, and you aren't storing the cursor item, the cursor item will be stored/dropped
     * @param fromSlot the location of the item, -1 to store the item being carried by the cursor
     * @param indexCheck the bounds of the "storage" - if the predicate returns false, that slot will not be used as storage
     */
    public static boolean storeItem(int fromSlot, Predicate<Integer> indexCheck) {
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu == null) return false;

        // if fromSlot is null assume the item is being carried
        if (fromSlot >= 0) {
            if (menu.slots.get(fromSlot).getItem().isEmpty()) return false;

            // if there is already an item in the hand, drop it.
            if (!menu.getCarried().isEmpty()) {
                storeItem(-1, indexCheck);
            }

            gameMode.handleContainerInput(menu.containerId, fromSlot, 0, ContainerInput.PICKUP, minecraft.player);
        } else if (menu.getCarried().isEmpty()) {
            return true;
        }

        // sort the slots so full slots will be checked first
        List<Slot> slots = new ArrayList<>(menu.slots);
        slots.sort((a, b) -> Boolean.compare(a.getItem().isEmpty(), b.getItem().isEmpty()));

        int count = menu.getCarried().getCount();
        for (Slot slot : slots) {
            if (count <= 0) break;
            if (indexCheck.test(slot.index) && (ItemStack.isSameItemSameComponents(menu.getCarried(), slot.getItem()) || slot.getItem().isEmpty())) {
                int slotCount = slot.getItem().getCount();
                if (slotCount < slot.getMaxStackSize()) {
                    count -= Math.max(0, slot.getMaxStackSize() - slotCount);
                    gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, minecraft.player);
                }
            }
        }
        return count <= 0 || menu.getCarried().isEmpty();
    }

    /**
     * Drops an item
     * @param slot the slot to drop the item from, -1 to drop the cursor item
     * @param wholeStack drop the whole stack or a single item
     * @param force If we should perform the drop even if the item is air for the client
     */
    /**
     * Moves an item from a slot to a target slot by picking up from source and placing into target.
     * Stores any overflow back into the player inventory.
     * @param menu the container menu
     * @param fromSlotIndex the index in the slot list to take from
     * @param toSlotId the container slot id to place into
     */
    public static void moveItemToSlot(AbstractContainerMenu menu, int fromSlotIndex, int toSlotId) {
        assert Minecraft.getInstance().gameMode != null;
        storeItem(-1, i -> i > 4);
        Minecraft.getInstance().gameMode.handleContainerInput(
                menu.containerId, menu.getSlot(fromSlotIndex).index, 0, ContainerInput.PICKUP,
                Minecraft.getInstance().player);
        Minecraft.getInstance().gameMode.handleContainerInput(
                menu.containerId, toSlotId, 0, ContainerInput.PICKUP,
                Minecraft.getInstance().player);
        storeItem(-1, i -> i > 4);
    }

    /**
     * 玩家背包槽位（合成网格 / 工作区 / 结果槽的 {@code container} 都不是 {@link Inventory}）。
     */
    public static boolean isPlayerInventorySlot(Slot slot) {
        return slot.container instanceof Inventory;
    }

    /**
     * 工作区里的物品能不能**全部**退回背包——vanilla
     * {@code ServerPlaceRecipe#testClearGrid()} 的等价判定：每一堆要么能并进背包里同类且还有
     * 空间的堆，要么能占一个空格。
     *
     * <p>为什么要先判定：退回走 {@link #storeItem}（"放不下就留在光标上"），不判定就退的话，
     * 背包满时会把工作区的物品丢到玩家光标上。vanilla 在这种情况下**直接放弃这次点击**
     * （{@code ServerPlaceRecipe.placeRecipe} 的 {@code if (!bl && !testClearGrid()) return NOTHING}），
     * 这里同义。</p>
     */
    public static boolean canReturnSlotsToInventory(AbstractContainerMenu menu, Predicate<Slot> workspaceSlot) {
        // 背包现状模型：可合并/可占用的堆 + 剩余空格数
        List<ItemStack> homes = new ArrayList<>();
        int freeSlots = 0;
        for (Slot slot : menu.slots) {
            if (!isPlayerInventorySlot(slot)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                ++freeSlots;
            } else {
                homes.add(stack.copy());
            }
        }

        // {@link #storeItem} 会先把手上的东西放进背包——先按它占位，免得它把工作区那堆的位置挤掉
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            freeSlots = insertIntoInventoryModel(homes, freeSlots, carried);
            if (freeSlots < 0) return false;
        }

        for (Slot slot : menu.slots) {
            if (!workspaceSlot.test(slot)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            freeSlots = insertIntoInventoryModel(homes, freeSlots, stack);
            if (freeSlots < 0) return false;
        }
        return true;
    }

    /**
     * 把 {@code stack} 塞进"背包现状"模型：先并进同类且还有空间的堆，再占一个空格。
     *
     * @return 新的空格数；{@code -1} = 塞不下
     */
    private static int insertIntoInventoryModel(List<ItemStack> homes, int freeSlots, ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack home : homes) {
            if (remaining <= 0) break;
            if (!ItemStack.isSameItemSameComponents(home, stack)) continue;
            int room = home.getMaxStackSize() - home.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, remaining);
            home.grow(moved);
            remaining -= moved;
        }
        if (remaining > 0) {
            if (freeSlots <= 0) return -1;
            --freeSlots;
            ItemStack copy = stack.copy();
            copy.setCount(remaining);
            homes.add(copy);
        }
        return freeSlots;
    }

    /**
     * 把工作区槽位里的物品**全部退回玩家背包**——vanilla
     * {@code ServerPlaceRecipe#clearGrid()} 的客户端等价（"材料不齐时点配方"的语义：
     * 工作区先清干净，再显示缺料引导）。
     *
     * <p>调用前必须先用 {@link #canReturnSlotsToInventory} 判定：{@link #storeItem} 放不下时会
     * 把物品留在光标上。</p>
     */
    public static void returnSlotsToInventory(AbstractContainerMenu menu, Predicate<Slot> workspaceSlot) {
        // ⚠️ 传**菜单槽位序号**（= handleContainerInput 的 slotId / menu.slots 的下标），
        // 不是 Slot#index（那是槽位在它自己容器里的序号，玩家背包槽两者并不相同）
        for (int i = 0; i < menu.slots.size(); ++i) {
            Slot slot = menu.slots.get(i);
            if (!workspaceSlot.test(slot)) continue;
            if (slot.getItem().isEmpty()) continue;
            // storeItem 会先把手上的东西放进背包再拿这一堆——与放置路径（moveItemToSlot）同一约定
            storeItem(i, index -> isPlayerInventorySlot(menu.slots.get(index)));
        }
    }

    public static void dropItem(int slot, boolean wholeStack, boolean force) {
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu == null) return;

        ContainerInput type = ContainerInput.THROW;

        if (slot < 0) {
            slot = -999;
            type = ContainerInput.PICKUP;
            if (!force && menu.getCarried().isEmpty()) return;
        } else if (!force && menu.slots.get(slot).getItem().isEmpty()) {
            return;
        }

        gameMode.handleContainerInput(menu.containerId, slot, wholeStack ? 0 : 1, type, minecraft.player);
    }

}
