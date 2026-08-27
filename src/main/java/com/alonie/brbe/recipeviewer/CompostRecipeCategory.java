package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.ComposterBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The compost category: a pure info sheet like the smelting-fuel category —
 * no recipe buttons, a grid of compostable items (or the queried item alone),
 * and hovering a cell shows the compost chance ("概率：25%") as an info line
 * right in the tooltip.
 *
 * <p>Data source is vanilla itself ({@link ComposterBlock#COMPOSTABLES}, the
 * same registry JEI's {@code CompostingRecipeMaker} reads), so the category
 * works with and without a JEI runtime.  A usage (U) query of a compostable
 * item shows that item alone; a usage query of the composter shows every
 * compostable item (JEI station semantics, sorted by chance like JEI).</p>
 */
public final class CompostRecipeCategory implements RecipeViewerCategory {

    private static final String STATION_TYPE = "minecraft:compostable";

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
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        // Info sheet: no recipe entries.
        return List.of();
    }

    @Override
    public boolean hasContent(ItemStack target, boolean usage) {
        return usage && (isCompostable(target) || isCompostStation(target));
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return STATION_TYPE.equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    @Override
    public boolean isGridCategory() {
        return true;
    }

    @Override
    public List<ItemStack> allGridItems() {
        return allCompostables();
    }

    @Override
    public List<ItemStack> gridItems(ItemStack target, boolean usage) {
        if (!usage || target == null || target.isEmpty()) return List.of();
        if (isCompostable(target)) return List.of(target);
        if (isCompostStation(target)) return allCompostables();
        return List.of();
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return hasContent(target, true) ? 2 : -1;
    }

    /** Whether {@code stack} is a registered compostable (chance &gt; 0). */
    public boolean isCompostable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && chanceFor(stack) > 0f;
    }

    /** Whether {@code target} is the composter block. */
    public boolean isCompostStation(ItemStack target) {
        return STATION_TYPE.equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    /** Compost chance of {@code stack} (0..1), 0 when not compostable. */
    public float chanceFor(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? 0f : ComposterBlock.COMPOSTABLES.getOrDefault(stack.getItem(), 0f);
    }

    /** Every registered compostable item, sorted by chance ascending (JEI
     *  {@code CompostingRecipeMaker} order). */
    public List<ItemStack> allCompostables() {
        List<Map.Entry<ItemLike, Float>> entries =
                new ArrayList<>(ComposterBlock.COMPOSTABLES.entrySet());
        entries.sort(Comparator.comparingDouble(Map.Entry::getValue));
        List<ItemStack> out = new ArrayList<>(entries.size());
        for (Map.Entry<ItemLike, Float> entry : entries) {
            out.add(new ItemStack((Item) entry.getKey()));
        }
        return out;
    }
}
