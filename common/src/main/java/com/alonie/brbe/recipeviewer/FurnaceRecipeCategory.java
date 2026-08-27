package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The furnace category: smelting recipes (furnace, blast furnace, smoker,
 * campfire).  R = which furnace recipes produce {@code target}; U = what
 * {@code target} smelts into.  1.21.1 版：smelting 类型聚合（blasting/smoking/
 * campfire 的 RecipeType 并入 smelting 注册——当前 engine 只注册 smelting，
 * 多类型合并待后续 RecipeViewerIndex 扩展）。
 */
public final class FurnaceRecipeCategory implements RecipeViewerCategory {

    private static final String TYPE = "minecraft:smelting";

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
        return menu instanceof AbstractFurnaceMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        // 熔炉家族工作站：usage 查询显示该站能烧的全部配方（JEI 语义）。
        // 1.21.1 简化：目标物品是熔炉/鼓风炉/烟熏炉方块。
        return target != null && !target.isEmpty() && (
                target.is(net.minecraft.world.level.block.Blocks.FURNACE.asItem())
                        || target.is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE.asItem())
                        || target.is(net.minecraft.world.level.block.Blocks.SMOKER.asItem()));
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 1 : -1;
    }
}
