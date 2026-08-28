package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * The smithing category.  1.21.1 版。
 */
public final class SmithingRecipeCategory implements RecipeViewerCategory {

    private static final String TYPE = "minecraft:smithing";

    @Override
    public String id() {
        return "smithing";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.SMITHING_TABLE);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.smithing");
    }

    @Override
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        return usage
                ? RecipeViewerEngine.usagesFor(TYPE, target)
                : RecipeViewerEngine.resultsFor(TYPE, target);
    }

    @Override
    public List<RecipeHolder<?>> allEntries() {
        return RecipeViewerEngine.allRecipes(TYPE);
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        return RecipeViewerEngine.hasContent(TYPE, target, false)
                || RecipeViewerEngine.hasContent(TYPE, target, true);
    }

    @Override
    public boolean appliesToMenu(AbstractContainerMenu menu) {
        return menu instanceof SmithingMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return target != null && !target.isEmpty()
                && target.is(net.minecraft.world.level.block.Blocks.SMITHING_TABLE.asItem());
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 1 : -1;
    }
}
