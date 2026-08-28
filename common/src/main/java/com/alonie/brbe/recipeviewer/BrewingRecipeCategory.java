package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.brewingstand.BrewableResult;
import com.alonie.brbe.loaders.PotionLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * The brewing category: potion recipes (via PotionLoader's scanned
 * PotionBrewing mixes — the same data source as the brewing recipe book).
 * Rendered as a standalone grid (potions), like the fuel category.  1.21.1 版：
 * 无 RecipeDisplay 体系，直接输出 BrewableResult 的结果药水。
 */
public final class BrewingRecipeCategory implements RecipeViewerCategory {

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
        return Component.translatable("brbe.category.brewing");
    }

    @Override
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        // Info sheet / grid: no RecipeHolder entries.
        return List.of();
    }

    @Override
    public boolean hasContent(ItemStack target, boolean usage) {
        return usage && (isBrewingStation(target) || isPotionBase(target));
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return target != null && !target.isEmpty()
                && target.is(net.minecraft.world.level.block.Blocks.BREWING_STAND.asItem());
    }

    @Override
    public boolean isGridCategory() {
        return true;
    }

    @Override
    public List<ItemStack> allGridItems() {
        // Every potion the brewing book knows (result items)
        List<ItemStack> out = new ArrayList<>();
        for (BrewableResult result : PotionLoader.POTIONS) {
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level == null) continue;
                ItemStack stack = result.getResult(
                        mc.level.registryAccess(), null);
                if (!stack.isEmpty()) out.add(stack);
            } catch (Exception e) {
                // skip unresolvable potion
            }
        }
        return out;
    }

    @Override
    public List<ItemStack> gridItems(ItemStack target, boolean usage) {
        if (!usage || target == null || target.isEmpty()) return List.of();
        if (isBrewingStation(target)) return allGridItems();
        // Potion-base lookup narrowed below (simplified: base → all potions)
        if (isPotionBase(target)) return allGridItems();
        return List.of();
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return (isBrewingStation(target) || isPotionBase(target)) ? 1 : -1;
    }

    private boolean isBrewingStation(ItemStack target) {
        return target != null && !target.isEmpty()
                && target.is(net.minecraft.world.level.block.Blocks.BREWING_STAND.asItem());
    }

    /** Potion base: water bottle / awkward potion / nether wart etc. — simplified
     *  to "any item the brewing book can brew with". */
    private boolean isPotionBase(ItemStack target) {
        return target != null && !target.isEmpty()
                && (target.is(net.minecraft.world.item.Items.WATER_BUCKET)
                        || target.is(net.minecraft.world.item.Items.GLASS_BOTTLE)
                        || target.is(net.minecraft.world.item.Items.NETHER_WART)
                        || target.is(net.minecraft.world.item.Items.POTION)
                        || target.is(net.minecraft.world.item.Items.SPLASH_POTION));
    }
}
