package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * The crafting-table category: the existing R/U behaviour (results of the item
 * are crafting recipes; usages are recipes that take it as a material), backed
 * by {@link RecipeViewerIndex} (the vanilla recipe book's known crafting_*
 * recipes).
 */
public final class CraftingRecipeCategory implements RecipeViewerCategory {

    @Override
    public String id() {
        return "crafting";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CRAFTING_TABLE);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.crafting");
    }

    @Override
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        return usage ? RecipeViewerIndex.usagesFor(target) : RecipeViewerIndex.resultsFor(target);
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        return !target.isEmpty();
    }

    @Override
    public int defaultPriority(ItemStack target) {
        // Only category today; always applicable.  Future furnace/smithing
        // categories return a higher priority for items they can process.
        return 0;
    }
}
