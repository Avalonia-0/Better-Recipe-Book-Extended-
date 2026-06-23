package com.alonie.brbe.util;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks per-item total counts across inventory slots to detect which
 * items changed between {@code updateCollections} calls.
 *
 * <p>Unlike the binary {@code slotHash}, this produces a precise set of
 * items whose presence or count changed, enabling incremental re-evaluation
 * of only the affected {@code RecipeCollection} objects.
 *
 * <p>Returns {@code null} on the first call for a menu class (meaning
 * "everything changed — need full pipeline").
 */
public final class SlotTracker {

    private static final Map<Class<?>, Map<Item, Integer>> PREVIOUS = new HashMap<>();

    private SlotTracker() {}

    /**
     * Returns items whose total count across all slots changed since the
     * last call for this menu class.  Returns {@code null} if this is the
     * first call (no previous state to diff against).
     */
    public static Set<Item> changedItems(Class<?> menuClass, NonNullList<Slot> slots) {
        Map<Item, Integer> current = new HashMap<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                current.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        Map<Item, Integer> prev = PREVIOUS.get(menuClass);
        PREVIOUS.put(menuClass, current);

        if (prev == null) return null; // First call — full pipeline needed

        Set<Item> changed = new HashSet<>();
        for (var e : current.entrySet()) {
            Integer pc = prev.get(e.getKey());
            if (pc == null || !pc.equals(e.getValue())) {
                changed.add(e.getKey());
            }
        }
        for (Item item : prev.keySet()) {
            if (!current.containsKey(item)) {
                changed.add(item);
            }
        }
        return changed;
    }

    public static void clear() {
        PREVIOUS.clear();
    }
}
