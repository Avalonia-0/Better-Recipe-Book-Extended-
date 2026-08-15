package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds vanilla {@link RecipeDisplayEntry}s from a plugin's extracted slot
 * data so mod recipes can flow through the same BRBE front-end as vanilla
 * entries.  A {@link ShapelessCraftingRecipeDisplay} of item slot displays is
 * used; craftability is left unknown (empty crafting requirements).  Synthetic
 * ids live in the {@code Integer.MIN_VALUE} range, disjoint from
 * {@code VanillaRecipeCache}'s {@code -1,-2,…} range.
 *
 * <p>A multi-output recipe is split into one entry per product ({@link
 * #createForOutput}), so lookup gates on that single product while all split
 * entries keep the same inputs and backing recipe — the "guts" are shared.
 */
public final class SyntheticRecipeDisplayEntryFactory {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(Integer.MIN_VALUE);

    private SyntheticRecipeDisplayEntryFactory() {}

    /** Build one synthetic entry whose result is one OUTPUT slot of the recipe —
     *  {@code outputSlot} holds that slot's candidate variant stacks (all of them,
     *  kept together as one product).  The inputs and workstation are shared by
     *  every split entry of the recipe. */
    public static RecipeDisplayEntry createForOutput(List<SlotData> slots, List<ItemStack> stations,
                                                     List<ItemStack> outputSlot) {
        List<SlotDisplay> inputDisplays = new ArrayList<>();
        if (slots != null) {
            for (SlotData slot : slots) {
                if (slot.role() == RecipeIngredientRole.INPUT
                        || slot.role() == RecipeIngredientRole.CRAFTING_STATION) {
                    inputDisplays.add(toSlotDisplay(slot.stacks()));
                }
                // RENDER_ONLY slots are not part of lookup
            }
        }
        SlotDisplay result = (outputSlot == null || outputSlot.isEmpty())
                ? SlotDisplay.Empty.INSTANCE
                : toSlotDisplay(outputSlot);
        SlotDisplay station = stations == null || stations.isEmpty()
                ? SlotDisplay.Empty.INSTANCE
                : toSlotDisplay(stations);
        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(inputDisplays, result, station);
        RecipeDisplayId id = new RecipeDisplayId(NEXT_ID.getAndIncrement());
        return new RecipeDisplayEntry(id, display, OptionalInt.empty(),
                RecipeBookCategories.CRAFTING_MISC, Optional.empty());
    }

    /** One slot display from a slot's concrete item stacks: empty → Empty,
     *  single → ItemSlotDisplay, several → Composite of item displays. */
    private static SlotDisplay toSlotDisplay(List<ItemStack> stacks) {
        List<SlotDisplay> children = new ArrayList<>();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    children.add(new SlotDisplay.ItemSlotDisplay(stack.getItem()));
                }
            }
        }
        if (children.isEmpty()) return SlotDisplay.Empty.INSTANCE;
        if (children.size() == 1) return children.get(0);
        return new SlotDisplay.Composite(children);
    }
}
