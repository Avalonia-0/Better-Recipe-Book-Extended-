package com.alonie.brbe.recipeviewer;

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
 * the "ingredient" role, so this category only activates on a usage (U) query
 * of a fuel item.  Rendered standalone (no recipe buttons — fuel is not a
 * recipe), so {@link #query} is unused and the overlay draws the fuel grid
 * itself via {@link #fuelItems()}.
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
    public boolean hasContent(ItemStack target, boolean usage) {
        return usage && isFuel(target);
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return isFuel(target) ? 2 : -1;
    }

    /** Burn time (ticks) of {@code fuel}, or 0 when unresolvable. */
    public int burnDuration(ItemStack fuel) {
        FuelValues values = fuelValues();
        return values == null || fuel == null || fuel.isEmpty() ? 0 : values.burnDuration(fuel);
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
