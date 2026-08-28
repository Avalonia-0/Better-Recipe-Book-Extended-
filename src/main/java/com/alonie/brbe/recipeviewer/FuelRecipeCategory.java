package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.block.entity.FuelValues;

import java.util.List;

/**
 * The smelting-fuel category: every fuel item, whose tooltip shows how many
 * items it can smelt in the furnace / blast furnace / smoker.  A fuel plays
 * the "ingredient" role, so this category activates on a usage (U) query of a
 * fuel item (showing that fuel's burn info) and of a fuel-burning workstation
 * (furnace / blast furnace / smoker — showing the fuel it can take).  Rendered
 * standalone (no recipe buttons — fuel is not a recipe), so {@link #query} is
 * unused and the overlay draws the fuel grid itself.
 */
public final class FuelRecipeCategory implements RecipeViewerCategory {

    /** Furnace-family workstation recipe types that actually burn fuel
     *  (campfire cannot take fuel and is excluded). */
    private static final List<String> FUEL_STATION_TYPES = List.of(
            "minecraft:smelting", "minecraft:blasting", "minecraft:smoking");

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
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        return List.of();
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        return isFuel(target);
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
        return allFuelItems();
    }

    @Override
    public List<ItemStack> gridItems(ItemStack target, boolean usage) {
        if (!usage || target == null || target.isEmpty()) return List.of();
        if (isFuel(target)) return List.of(target);
        if (isFuelStation(target)) return allFuelItems();
        return List.of();
    }

    /** This category also activates on a usage query of a fuel-burning
     *  workstation: its usage view lists the fuel it can take. */
    @Override
    public boolean appliesToStation(ItemStack target) {
        return isFuelStation(target);
    }

    @Override
    public boolean hasContent(ItemStack target, boolean usage) {
        return usage && (isFuel(target) || isFuelStation(target));
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return (isFuel(target) || isFuelStation(target)) ? 2 : -1;
    }

    /** Whether {@code target} is a furnace-family workstation that burns fuel
     *  (furnace / blast furnace / smoker — campfire cannot take fuel). */
    public boolean isFuelStation(ItemStack target) {
        String typeId = RecipeViewerIndex.stationTypeIdFor(target);
        return typeId != null && FUEL_STATION_TYPES.contains(typeId);
    }

    /** Burn time (ticks) of {@code fuel}, or 0 when unresolvable. */
    public int burnDuration(ItemStack fuel) {
        FuelValues values = fuelValues();
        return values == null || fuel == null || fuel.isEmpty() ? 0 : values.burnDuration(fuel);
    }

    /** Whether {@code stack} is a registered fuel in the current level. */
    public boolean isFuelItem(ItemStack stack) {
        return isFuel(stack);
    }

    /** Every fuel item registered in the current level, sorted by burn time
     *  ascending — mirroring JEI's {@code FuelRecipeMaker}, which only collects
     *  items whose {@code getBurnTime > 0} and orders them by burn time. */
    public List<ItemStack> allFuelItems() {
        FuelValues values = fuelValues();
        if (values == null) return List.of();
        return values.fuelItems().stream()
                .filter(item -> values.burnDuration(item.getDefaultInstance()) > 0)
                .sorted(java.util.Comparator.comparingInt(
                        item -> values.burnDuration(item.getDefaultInstance())))
                .map(ItemStack::new)
                .toList();
    }

    private boolean isFuel(ItemStack target) {
        if (target == null || target.isEmpty()) return false;
        FuelValues values = fuelValues();
        return values != null && values.isFuel(target);
    }

    private FuelValues fuelValues() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.fuelValues();
    }
}
