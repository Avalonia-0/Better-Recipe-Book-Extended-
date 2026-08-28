package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * The anvil category (JEI {@code minecraft:anvil} type): repair and
 * book-enchantment recipes.  R = which anvil recipes produce {@code target};
 * U = what {@code target} can be combined with on the anvil (as input).
 *
 * <p>Recipe entries are collected by the JEI-plugin indexer from JEI's own
 * vanilla plugin (runtime-built recipes, no datapack holders), each carrying
 * its native JEI layout + render entry, so preview / pin show the complete
 * JEI UI (anvil background, slot backgrounds, plus sign, arrow, XP-cost text).
 * The anvil is a no-recipe-book workstation: with "hide objects of workstations
 * without a recipe book" its station connection is cut (like the stonecutter)
 * and its recipe objects are filtered from every query.</p>
 */
public final class AnvilRecipeCategory implements RecipeViewerCategory {

    private static final String TYPE = "minecraft:anvil";

    @Override
    public String id() {
        return "anvil";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ANVIL);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.anvil");
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
        return menu instanceof AnvilMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return TYPE.equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    @Override
    public List<ItemStack> stationIconsFor(RecipeDisplayEntry entry) {
        // The anvil workstation icons (all three anvil blocks): the engine
        // entries are synthetic (recipe-book category "crafting_misc"), so the
        // category-path lookup would mis-attribute the crafting table.
        return anvilStations();
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 1 : -1;
    }

    /** Every registered anvil block (vanilla has three variants). */
    public static List<ItemStack> anvilStations() {
        List<ItemStack> out = new ArrayList<>();
        for (Item item : List.of(Items.ANVIL, Items.CHIPPED_ANVIL, Items.DAMAGED_ANVIL)) {
            out.add(new ItemStack(item));
        }
        return out;
    }
}
