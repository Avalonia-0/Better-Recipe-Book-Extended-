package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.util.LootIntResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * The compost category: a pure info sheet like the smelting-fuel category —
 * no recipe buttons, a grid of compostable items (or the queried item alone),
 * and hovering a cell shows the compost chance ("概率：25%") as an info line
 * right in the tooltip.
 *
 * <p>Data source is vanilla itself — 26.3 replaced {@code ComposterBlock.
 * COMPOSTABLES} with the item data component {@code minecraft:compostable},
 * whose value is a loot-context provider of "layers added".  The tooltip keeps
 * showing the historical chance by taking the provider's expected value (see
 * {@link LootIntResolver}), which for the vanilla providers reproduces the old
 * numbers (low = 0.3, low_medium = 0.5, medium = 0.65, medium_high = 0.85,
 * always_add_one = 1.0).  The category therefore works with and without a JEI
 * runtime.  A usage (U) query of a compostable item shows that item alone; a
 * usage query of the composter shows every compostable item (JEI station
 * semantics, sorted by chance like JEI).</p>
 */
public final class CompostRecipeCategory implements RecipeViewerCategory {

    private static final String STATION_TYPE = "minecraft:compostable";

    @Override
    public String id() {
        return "compost";
    }

    @Override
    public java.util.List<String> jeiTypeUids() {
        return List.of("minecraft:compostable");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.COMPOSTER);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.compost");
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

    /** Whether {@code stack} is a registered compostable (has the component). */
    public boolean isCompostable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(DataComponents.COMPOSTABLE);
    }

    /** Whether {@code target} is the composter block. */
    public boolean isCompostStation(ItemStack target) {
        return STATION_TYPE.equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    /** Compost chance of {@code stack} (0..1), 0 when not compostable —
     *  26.3: 由 {@code minecraft:compostable} 组件的 provider 期望值推出
     *  （provider 本身是 loot 上下文相关的，客户端只能求期望近似）。 */
    public float chanceFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0f;
        Compostable compostable = stack.get(DataComponents.COMPOSTABLE);
        if (compostable == null) return 0f;
        return (float) Math.max(0.0, LootIntResolver.expected(compostable.layers()));
    }

    /**
     * Whether the compost chance of {@code stack} is <b>known</b> (the provider
     * could actually be resolved — see {@link LootIntResolver#resolvable}).
     *
     * <p>26.3 的 provider 注册表不同步到客户端：LAN/多机 + 非原版（数据包/mod
     * 新增）provider 解析不出来，此时 {@link #chanceFor} 会返回 0——tooltip 应当
     * <b>不显示</b>这一行，而不是显示误导性的"概率：0%"。</p>
     */
    public boolean chanceKnown(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Compostable compostable = stack.get(DataComponents.COMPOSTABLE);
        return compostable != null && LootIntResolver.resolvable(compostable.layers());
    }

    /** Every registered compostable item, sorted by chance ascending (JEI
     *  {@code CompostingRecipeMaker} order). */
    public List<ItemStack> allCompostables() {
        return BuiltInRegistries.ITEM.stream()
                .map(ItemStack::new)
                .filter(this::isCompostable)
                .sorted(java.util.Comparator.comparingDouble(this::chanceFor))
                .toList();
    }
}
