package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * The crafting-table category: the existing R/U behaviour (results of the item
 * are crafting recipes; usages are recipes that take it as a material), backed
 * by the query engine's {@code minecraft:crafting} type.
 */
public final class CraftingRecipeCategory implements RecipeViewerCategory {

    private static final String TYPE = "minecraft:crafting";

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
        return usage
                ? RecipeViewerEngine.usagesFor(TYPE, target)
                : RecipeViewerEngine.resultsFor(TYPE, target);
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        return !target.isEmpty();
    }

    @Override
    public boolean appliesToMenu(AbstractContainerMenu menu) {
        return menu instanceof AbstractCraftingMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return "minecraft:crafting".equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    @Override
    public List<ItemStack> stationIconsFor(RecipeDisplayEntry entry) {
        return RecipeViewerIndex.workstationsIconsFor(entry);
    }

    @Override
    public int defaultPriority(ItemStack target) {
        // Fallback category; furnace / fuel / stonecutter / smithing return a
        // higher priority for items they can process.
        return 0;
    }
}
