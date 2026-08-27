package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * The brewing category (JEI {@code minecraft:brewing} type): every potion
 * recipe JEI generates from the world's potion-brewing registry.  R = which
 * brewing recipes produce {@code target} (as output potion); U = what
 * {@code target} brews into (as ingredient or input potion).
 *
 * <p>Recipe entries are collected by the JEI-plugin indexer from JEI's own
 * vanilla plugin (runtime-built recipes, no datapack holders), each carrying
 * its native JEI layout + render entry, so preview / pin show the complete
 * JEI UI (brewing-stand background, slot backgrounds, bubbles, arrow,
 * brewing-steps text).  The brewing stand is a no-recipe-book workstation:
 * with "hide objects of workstations without a recipe book" its station
 * connection is cut (like the stonecutter) and its recipe objects are
 * filtered from every query.</p>
 */
public final class BrewingRecipeCategory implements RecipeViewerCategory {

    private static final String TYPE = "minecraft:brewing";

    @Override
    public String id() {
        return "brewing";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BREWING_STAND);
    }

    @Override
    public Component name() {
        return Component.translatable("zzzbrbe.category.brewing");
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
        return menu instanceof BrewingStandMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return TYPE.equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    @Override
    public List<ItemStack> stationIconsFor(RecipeDisplayEntry entry) {
        // The brewing-stand station icon: the engine entries are synthetic
        // (recipe-book category "crafting_misc"), so the category-path lookup
        // would mis-attribute the crafting table.
        return List.of(new ItemStack(Items.BREWING_STAND));
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 1 : -1;
    }
}
