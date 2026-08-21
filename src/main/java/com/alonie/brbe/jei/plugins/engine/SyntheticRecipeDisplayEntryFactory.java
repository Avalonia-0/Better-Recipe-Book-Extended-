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
 * <p>A multi-output recipe keeps ONE entry whose result carries every product
 * (a composite display): the viewer shows a single object that cycles through
 * the products instead of one split button per product.  The engine indexes
 * the entry under each product, so result lookup still gates per product.</p>
 */
public final class SyntheticRecipeDisplayEntryFactory {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(Integer.MIN_VALUE);

    private SyntheticRecipeDisplayEntryFactory() {}

    /** Build one synthetic entry whose result is the recipe's products —
     *  {@code products} holds every distinct player-obtainable product stack
     *  (all output slots combined), kept together as one composite result so
     *  the viewer object cycles through them. */
    public static RecipeDisplayEntry createForOutput(List<SlotData> slots, List<ItemStack> stations,
                                                     List<ItemStack> products) {
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
        SlotDisplay result = (products == null || products.isEmpty())
                ? SlotDisplay.Empty.INSTANCE
                : toSlotDisplay(products);
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
