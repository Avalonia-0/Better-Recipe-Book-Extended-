package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * The smelting-fuel category: every fuel item, whose tooltip shows how many
 * items it can smelt.  Rendered standalone (grid, no recipe buttons).
 * 1.21.1 版：燃料判定用 AbstractFurnaceBlockEntity.isFuel/getFuel。
 */
public final class FuelRecipeCategory implements RecipeViewerCategory {

    @Override
    public String id() {
        return "fuel";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.FURNACE);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.fuel");
    }

    @Override
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        return List.of();
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        return RecipeViewerIndex.isFuelItem(target);
    }

    @Override
    public boolean isFuelCategory() {
        return true;
    }

    @Override
    public boolean isGridCategory() {
        return true;
    }

    @Override
    public List<ItemStack> allGridItems() {
        return RecipeViewerIndex.allFuelItems();
    }

    @Override
    public List<ItemStack> gridItems(ItemStack target, boolean usage) {
        if (!usage || target == null || target.isEmpty()) return List.of();
        if (RecipeViewerIndex.isFuelItem(target)) return List.of(target);
        if (isFuelStation(target)) return RecipeViewerIndex.allFuelItems();
        return List.of();
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return isFuelStation(target);
    }

    @Override
    public boolean hasContent(ItemStack target, boolean usage) {
        return usage && (RecipeViewerIndex.isFuelItem(target) || isFuelStation(target));
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return (RecipeViewerIndex.isFuelItem(target) || isFuelStation(target)) ? 2 : -1;
    }

    private boolean isFuelStation(ItemStack target) {
        return target != null && !target.isEmpty() && (
                target.is(net.minecraft.world.level.block.Blocks.FURNACE.asItem())
                        || target.is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE.asItem())
                        || target.is(net.minecraft.world.level.block.Blocks.SMOKER.asItem()));
    }

    /** Burn time (ticks) of {@code fuel}, or 0. */
    public int burnDuration(ItemStack fuel) {
        return RecipeViewerIndex.burnDuration(fuel);
    }
}
