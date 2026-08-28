package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * The crafting-table category: the existing R/U behaviour (results of the item
 * are crafting recipes; usages are recipes that take it as a material), backed
 * by the query engine's {@code minecraft:crafting} type.
 *
 * <p>1.21.1 版：数据源为旧 Recipe 模型（RecipeHolder），station 判定
 * （appliesToStation/stationIconsFor）从简——1.21.1 无 RecipeViewerIndex
 * 的 workstations 聚合，工作站图标由 UI 层按工作站 block 查询提供。</p>
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
        return !target.isEmpty();
    }

    @Override
    public boolean appliesToMenu(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu;
    }

    @Override
    public int defaultPriority(ItemStack target) {
        // Fallback category; furnace / fuel / stonecutter / smithing return a
        // higher priority for items they can process.
        return 0;
    }
}
