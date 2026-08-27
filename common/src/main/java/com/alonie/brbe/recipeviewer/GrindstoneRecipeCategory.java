package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * The grindstone category.  1.21.1 版：1.21.1 无 JEI 运行时构建的 grindstone
 * 配方数据源（无 RecipeType），本类别作为**工作站信息类别**：U 查询研磨石
 * 显示信息说明（修复/去附魔），不提供配方条目（数据源缺失时降级）。
 */
public final class GrindstoneRecipeCategory implements RecipeViewerCategory {

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
        return Component.translatable("zzzbrbe.category.grindstone");
    }

    @Override
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        // 1.21.1 原版无 grindstone RecipeType；配方条目走 JEI 运行时（queryJei）。
        return List.of();
    }

    @Override
    public List<RecipeViewerEngine.JeiEntry> queryJei(ItemStack target, boolean usage) {
        return usage
                ? RecipeViewerEngine.jeiUsagesFor(TYPE, target)
                : RecipeViewerEngine.jeiResultsFor(TYPE, target);
    }

    @Override
    public List<RecipeViewerEngine.JeiEntry> allJeiEntries() {
        return RecipeViewerEngine.allJeiRecipes(TYPE);
    }

    private static final String TYPE = "minecraft:grindstone";

    @Override
    public boolean hasContent(ItemStack target, boolean usage) {
        return (usage && appliesToStation(target))
                || RecipeViewerEngine.hasJeiContent(TYPE, target, usage);
    }

    @Override
    public boolean appliesToMenu(AbstractContainerMenu menu) {
        return menu instanceof GrindstoneMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return target != null && !target.isEmpty()
                && target.is(net.minecraft.world.level.block.Blocks.GRINDSTONE.asItem());
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesToStation(target) ? 1 : -1;
    }
}
