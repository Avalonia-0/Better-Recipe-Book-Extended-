package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The furnace category: smelting recipes (furnace, blast furnace, smoker,
 * campfire).  R = which furnace recipes produce {@code target} (as result);
 * U = what {@code target} smelts into (as ingredient).  Aggregates the four
 * JEI furnace recipe types and dedupes identical smelting content (registered
 * once per station) into a single representative.
 */
public final class FurnaceRecipeCategory implements RecipeViewerCategory {

    private static final List<String> FURNACE_TYPES = List.of(
            "minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking");

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
        return Component.translatable("zzzbrbe.category.furnace");
    }

    @Override
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        return usage ? mergeUsages(target) : mergeResults(target);
    }

    @Override
    public List<RecipeDisplayEntry> allEntries() {
        List<RecipeDisplayEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String type : FURNACE_TYPES) {
            for (RecipeDisplayEntry entry : RecipeViewerEngine.allRecipes(type)) {
                FurnaceRecipeDisplay display = RecipeViewerIndex.asFurnace(entry);
                if (display != null && seen.add(RecipeViewerIndex.furnaceContentKey(display))) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    private List<RecipeDisplayEntry> mergeResults(ItemStack target) {
        List<RecipeDisplayEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String type : FURNACE_TYPES) {
            for (RecipeDisplayEntry entry : RecipeViewerEngine.resultsFor(type, target)) {
                FurnaceRecipeDisplay display = RecipeViewerIndex.asFurnace(entry);
                if (display != null && seen.add(RecipeViewerIndex.furnaceContentKey(display))) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    private List<RecipeDisplayEntry> mergeUsages(ItemStack target) {
        List<RecipeDisplayEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String type : FURNACE_TYPES) {
            for (RecipeDisplayEntry entry : RecipeViewerEngine.usagesFor(type, target)) {
                FurnaceRecipeDisplay display = RecipeViewerIndex.asFurnace(entry);
                if (display != null && seen.add(RecipeViewerIndex.furnaceContentKey(display))) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        for (String type : FURNACE_TYPES) {
            if (RecipeViewerEngine.hasContent(type, target, false)
                    || RecipeViewerEngine.hasContent(type, target, true)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean appliesToMenu(AbstractContainerMenu menu) {
        // Furnace, blast furnace and smoker screens all count as the furnace
        // station.
        return menu instanceof AbstractFurnaceMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        // A furnace-family workstation's usage view lists every recipe its
        // station can cook (JEI: the station is a condition of those recipes).
        return RecipeViewerIndex.isFurnaceStation(target);
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
