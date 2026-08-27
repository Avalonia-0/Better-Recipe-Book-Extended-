package com.alonie.brbe.recipeviewer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.ComposterBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The compost category: a pure info sheet like the smelting-fuel category —
 * no recipe buttons, a grid of compostable items, and hovering a cell shows
 * the compost chance.  1.21.1 版：数据源 ComposterBlock.COMPOSTABLES。
 */
public final class CompostRecipeCategory implements RecipeViewerCategory {

    @Override
    public String id() {
        return "compost";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.COMPOSTER);
    }

    @Override
    public Component name() {
        return Component.translatable("zzzbrbe.category.compost");
    }

    @Override
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        // Info sheet: no recipe entries.
        return List.of();
    }

    @Override
    public boolean hasContent(ItemStack target, boolean usage) {
        return usage && (isCompostable(target) || appliesToStation(target));
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return target != null && !target.isEmpty()
                && target.is(net.minecraft.world.level.block.Blocks.COMPOSTER.asItem());
    }

    @Override
    public boolean isGridCategory() {
        return true;
    }

    @Override
    public List<ItemStack> allGridItems() {
        List<ItemStack> out = new ArrayList<>();
        for (var entry : ComposterBlock.COMPOSTABLES.object2FloatEntrySet()) {
            ItemLike like = entry.getKey();
            if (like == null) continue;
            ItemStack stack = new ItemStack(like.asItem());
            if (!stack.isEmpty()) out.add(stack);
        }
        out.sort((a, b) -> Float.compare(chanceOf(b), chanceOf(a)));
        return out;
    }

    @Override
    public List<ItemStack> gridItems(ItemStack target, boolean usage) {
        if (!usage || target == null || target.isEmpty()) return List.of();
        if (isCompostable(target)) return List.of(target);
        if (appliesToStation(target)) return allGridItems();
        return List.of();
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return (isCompostable(target) || appliesToStation(target)) ? 1 : -1;
    }

    /** Compost chance (0..1) of {@code item}, or 0 when not compostable. */
    public float chanceOf(ItemLike item) {
        return ComposterBlock.COMPOSTABLES.getOrDefault(item, 0.0F);
    }

    /** Compost chance of {@code stack} (0 when not compostable). */
    public float chanceOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0F;
        return chanceOf(stack.getItem());
    }

    private boolean isCompostable(ItemStack target) {
        return target != null && !target.isEmpty()
                && ComposterBlock.COMPOSTABLES.containsKey(target.getItem());
    }
}
