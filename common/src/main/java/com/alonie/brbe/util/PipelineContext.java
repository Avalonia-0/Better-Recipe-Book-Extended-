package com.alonie.brbe.util;

import com.alonie.brbe.PinnedRecipeManager;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;

/**
 * Immutable input context for {@link RecipePipeline#evaluate}.
 *
 * <p>All configuration and environment state is read once at the Mixin
 * entry point and packaged here so pipeline stages don't reach into
 * global config/minecraft singletons.
 */
public final class PipelineContext {

    public final Set<Item> inventoryItems;
    public final NonNullList<Slot> menuSlots;
    public final boolean onInventoryScreen;
    public final boolean partialMarkingEnabled;
    public final boolean brbeSortEnabled;
    public final boolean showAllRecipesInSurvival;
    public final boolean isFiltering;
    public final boolean inventoryChanged;
    public final PinnedRecipeManager pinnedManager;
    /** Result slot index of the menu, excluded from material counting. */
    public final int resultSlotIndex;
    /** 鼠标拿起物品（作为物品栏一部分参与检测），可为空。 */
    public final ItemStack carried;

    private PipelineContext(Builder b) {
        this.inventoryItems = b.inventoryItems;
        this.menuSlots = b.menuSlots;
        this.onInventoryScreen = b.onInventoryScreen;
        this.partialMarkingEnabled = b.partialMarkingEnabled;
        this.brbeSortEnabled = b.brbeSortEnabled;
        this.showAllRecipesInSurvival = b.showAllRecipesInSurvival;
        this.isFiltering = b.isFiltering;
        this.inventoryChanged = b.inventoryChanged;
        this.pinnedManager = b.pinnedManager;
        this.resultSlotIndex = b.resultSlotIndex;
        this.carried = b.carried;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the total count of items in the menu slots that match
     * any of the given ingredient's accepted items.
     */
    public int countMatching(Ingredient ingredient) {
        if (ingredient.isEmpty()) return Integer.MAX_VALUE;
        int total = 0;
        for (Slot slot : menuSlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static final class Builder {
        private Set<Item> inventoryItems = Set.of();
        private NonNullList<Slot> menuSlots;
        private boolean onInventoryScreen;
        private boolean partialMarkingEnabled = true;
        private boolean brbeSortEnabled = true;
        private boolean showAllRecipesInSurvival = true;
        private boolean isFiltering;
        private boolean inventoryChanged = true;
        private PinnedRecipeManager pinnedManager;
        private int resultSlotIndex = -1;
        private ItemStack carried = ItemStack.EMPTY;

        public Builder inventoryItems(Set<Item> v) { inventoryItems = v; return this; }
        public Builder menuSlots(NonNullList<Slot> v) { menuSlots = v; return this; }
        public Builder onInventoryScreen(boolean v) { onInventoryScreen = v; return this; }
        public Builder partialMarkingEnabled(boolean v) { partialMarkingEnabled = v; return this; }
        public Builder brbeSortEnabled(boolean v) { brbeSortEnabled = v; return this; }
        public Builder showAllRecipesInSurvival(boolean v) { showAllRecipesInSurvival = v; return this; }
        public Builder isFiltering(boolean v) { isFiltering = v; return this; }
        public Builder inventoryChanged(boolean v) { inventoryChanged = v; return this; }
        public Builder pinnedManager(PinnedRecipeManager v) { pinnedManager = v; return this; }
        public Builder resultSlotIndex(int v) { resultSlotIndex = v; return this; }
        public Builder carried(ItemStack v) { carried = v; return this; }

        public PipelineContext build() {
            return new PipelineContext(this);
        }
    }
}
