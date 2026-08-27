package com.alonie.brbe.recipeviewer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * The anvil category.  1.21.1 版：1.21.1 无 JEI 运行时构建的 anvil 配方数据源
 * （无 RecipeType），本类别作为**工作站信息类别**：U 查询铁砧显示信息说明
 * （修复/附魔组合），不提供配方条目（数据源缺失时降级）。
 */
public final class AnvilRecipeCategory implements RecipeViewerCategory {

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
        return Component.translatable("zzzbrbe.category.anvil");
    }

    @Override
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        // 数据源缺失（1.21.1 无 JEI anvil 运行时配方）：空条目，仅工作站信息。
        return List.of();
    }

    @Override
    public boolean hasContent(ItemStack target, boolean usage) {
        return usage && appliesToStation(target);
    }

    @Override
    public boolean appliesToMenu(AbstractContainerMenu menu) {
        return menu instanceof AnvilMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return target != null && !target.isEmpty()
                && target.is(Items.ANVIL)
                || target != null && !target.isEmpty() && (
                        target.is(net.minecraft.world.level.block.Blocks.ANVIL.asItem())
                        || target.is(net.minecraft.world.level.block.Blocks.CHIPPED_ANVIL.asItem())
                        || target.is(net.minecraft.world.level.block.Blocks.DAMAGED_ANVIL.asItem()));
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesToStation(target) ? 1 : -1;
    }
}
