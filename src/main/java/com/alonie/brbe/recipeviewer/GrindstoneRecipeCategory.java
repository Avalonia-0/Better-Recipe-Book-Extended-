package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * The grindstone category (JEI {@code minecraft:grindstone} type): repair and
 * disenchantment recipes.  R = which grindstone recipes produce {@code target};
 * U = what {@code target} can be processed on the grindstone (as input).
 *
 * <p>Recipe entries are collected by the JEI-plugin indexer from JEI's own
 * vanilla plugin (runtime-built recipes, no datapack holders), each carrying
 * its native JEI layout + render entry, so preview / pin show the complete
 * JEI UI (slot backgrounds, arrow, XP-reward text).  The grindstone is a
 * no-recipe-book workstation: with "hide objects of workstations without a
 * recipe book" its station connection is cut (like the stonecutter) and its
 * recipe objects are filtered from every query.</p>
 */
public final class GrindstoneRecipeCategory implements RecipeViewerCategory {

    private static final String TYPE = "minecraft:grindstone";

    @Override
    public String id() {
        return "grindstone";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GRINDSTONE);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.grindstone");
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
        return menu instanceof GrindstoneMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return TYPE.equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    @Override
    public List<ItemStack> stationIconsFor(RecipeDisplayEntry entry) {
        // The grindstone station icon: the engine entries are synthetic
        // (recipe-book category "crafting_misc"), so the category-path lookup
        // would mis-attribute the crafting table.
        return List.of(new ItemStack(Items.GRINDSTONE));
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 1 : -1;
    }
}
