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
 * {@code target} smelts into.  1.21.1 版：聚合四个熔炼 RecipeType
 * （smelting/blasting/smoking/campfire_cooking——index 各自独立注册，类别按
 * 1.21.11 语义合并展示）。
 */
public final class FurnaceRecipeCategory implements RecipeViewerCategory {

    /** 四个熔炼 RecipeType（index 各自注册，此处聚合）。 */
    private static final List<String> TYPES = List.of(
            "minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking");

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
        return Component.translatable("brbe.category.furnace");
    }

    @Override
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        // 聚合四个熔炼类型，按结果物品去重（同一配方跨类型重复出现时只留一个）。
        if (target == null || target.isEmpty()) return List.of();
        List<RecipeHolder<?>> out = new ArrayList<>();
        Set<RecipeHolder<?>> seen = new HashSet<>();
        for (String type : TYPES) {
            List<RecipeHolder<?>> hits = usage
                    ? RecipeViewerEngine.usagesFor(type, target)
                    : RecipeViewerEngine.resultsFor(type, target);
            for (RecipeHolder<?> h : hits) {
                if (seen.add(h)) out.add(h);
            }
        }
        return out;
    }

    @Override
    public List<RecipeHolder<?>> allEntries() {
        List<RecipeHolder<?>> out = new ArrayList<>();
        Set<RecipeHolder<?>> seen = new HashSet<>();
        for (String type : TYPES) {
            for (RecipeHolder<?> h : RecipeViewerEngine.allRecipes(type)) {
                if (seen.add(h)) out.add(h);
            }
        }
        return out;
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        if (target == null || target.isEmpty()) return false;
        boolean any = false;
        for (String type : TYPES) {
            if (RecipeViewerEngine.hasContent(type, target, false)
                    || RecipeViewerEngine.hasContent(type, target, true)) {
                any = true;
            }
        }
        return any;
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
