package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * The stonecutter category.  R = which stonecutter recipes produce
 * {@code target} (as result); U = what {@code target} cuts into (as input).
 */
public final class StonecuttingRecipeCategory implements RecipeViewerCategory {

    private static final String TYPE = "minecraft:stonecutting";

    @Override
    public String id() {
        return "stonecutting";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.STONECUTTER);
    }

    @Override
    public Component name() {
        return Component.translatable("zzzbrbe.category.stonecutting");
    }

    @Override
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        return usage
                ? RecipeViewerEngine.usagesFor(TYPE, target)
                : RecipeViewerEngine.resultsFor(TYPE, target);
    }

    @Override
    public List<RecipeDisplayEntry> allEntries() {
        return RecipeViewerEngine.allRecipes(TYPE);
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        return RecipeViewerEngine.hasContent(TYPE, target, false)
                || RecipeViewerEngine.hasContent(TYPE, target, true);
    }

    @Override
    public boolean appliesToMenu(AbstractContainerMenu menu) {
        return menu instanceof StonecutterMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return "minecraft:stonecutting".equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    @Override
    public List<ItemStack> stationIconsFor(RecipeDisplayEntry entry) {
        return RecipeViewerIndex.workstationsIconsFor(entry);
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 1 : -1;
    }
}
