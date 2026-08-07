package com.alonie.brbe.recipe;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipe;

import java.util.List;

public interface BRBSmithingRecipe extends SmithingRecipe, GenericRecipe {
    ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category);

    ItemStack getResult(ResourceKey<TrimMaterial> trimMaterialResourceKey, RegistryAccess registryAccess, BRBBookCategories.Category category);

    Ingredient getTemplate();

    ItemStack getBase();

    Ingredient getAddition();

    default boolean hasMaterials(NonNullList<Slot> slots, RegistryAccess registryAccess) {
        return hasTemplate(slots) && hasBase(slots, registryAccess) && hasAddition(slots);
    }

    default boolean hasTemplate(List<Slot> slots) {
        for (Slot slot : slots) {
            if (this.getTemplate().test(slot.getItem())) return true;
        }
        return false;
    }

    default boolean hasBase(List<Slot> slots, RegistryAccess registryAccess) {
        for (Slot slot : slots) {
            if (!slot.getItem().has(DataComponents.TRIM) && getBase().getItem().equals(slot.getItem().getItem()))
                return true;
        }
        return false;
    }

    default boolean hasAddition(List<Slot> slots) {
        for (Slot slot : slots) {
            if (getAddition().test(slot.getItem())) return true;
        }
        return false;
    }

    default String getTemplateType() {
        ItemStack[] items = getTemplate().getItems();
        if (items.length == 0) return "";
        var tipCtx = Item.TooltipContext.of(Minecraft.getInstance().player.level());
        return items[0].getTooltipLines(tipCtx, Minecraft.getInstance().player, TooltipFlag.NORMAL).get(1).getString();
    }

    default ResourceLocation id() {
        ItemStack[] items = getTemplate().getItems();
        if (items.length == 0) return BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.AIR);
        return BuiltInRegistries.ITEM.getKey(items[0].getItem());
    }

    @Override
    default String getSearchString(BRBBookCategories.Category category) {
        return this.getTemplateType();
    }

    @Override
    default boolean usesItem(ItemStack item) {
        if (this.getTemplate().test(item)) {
            return true;
        }
        if (this.getBase().getItem().equals(item.getItem())) {
            return true;
        }
        return this.getAddition().test(item);
    }
}
