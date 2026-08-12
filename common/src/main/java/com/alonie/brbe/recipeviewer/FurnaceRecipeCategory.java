package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * The furnace category: smelting recipes (furnace, blast furnace, smoker,
 * campfire).  R = which furnace recipes produce {@code target} (as result);
 * U = what {@code target} smelts into (as ingredient).  Picks the category by
 * default for items that can be smelted (or are smelting results).
 */
public final class FurnaceRecipeCategory implements RecipeViewerCategory {

    @Override
    public String id() {
        return "furnace";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.FURNACE);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.furnace");
    }

    @Override
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        return usage
                ? RecipeViewerIndex.furnaceUsagesFor(target)
                : RecipeViewerIndex.furnaceResultsFor(target);
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        // R = target is a smelting result; U = target is smelted as ingredient.
        return !RecipeViewerIndex.furnaceResultsFor(target).isEmpty()
                || !RecipeViewerIndex.furnaceUsagesFor(target).isEmpty();
    }

    @Override
    public int defaultPriority(ItemStack target) {
        // Prefer furnace over crafting when the item is smeltable or a smelting
        // result (e.g. hovering raw iron → smelting is the primary use).
        return appliesTo(target) ? 1 : -1;
    }

    /** Whether the entry is a furnace recipe (for hover rendering). */
    public static FurnaceRecipeDisplay displayOf(RecipeDisplayEntry entry) {
        return RecipeViewerIndex.asFurnace(entry);
    }
}
