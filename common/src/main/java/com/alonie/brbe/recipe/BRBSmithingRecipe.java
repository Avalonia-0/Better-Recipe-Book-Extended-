package com.alonie.brbe.recipe;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericRecipe;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public interface BRBSmithingRecipe extends GenericRecipe {
    ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category);

    ItemStack getResult(ResourceKey<TrimMaterial> trimMaterialResourceKey, RegistryAccess registryAccess, BRBBookCategories.Category category);

    Ingredient getTemplate();

    ItemStack getBase();

    Ingredient getAddition();

    default boolean requiresTemplate() {
        return true;
    }

    default boolean requiresAddition() {
        return true;
    }

    default boolean hasMaterials(NonNullList<Slot> slots, RegistryAccess registryAccess) {
        return hasTemplate(slots) && hasBase(slots, registryAccess) && hasAddition(slots);
    }

    default boolean hasPartialMaterials(NonNullList<Slot> slots, RegistryAccess registryAccess) {
        return (this.requiresTemplate() && hasTemplate(slots))
                || hasBase(slots, registryAccess)
                || (this.requiresAddition() && hasAddition(slots));
    }

    default boolean hasTemplate(List<Slot> slots) {
        if (!this.requiresTemplate()) {
            return true;
        }

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
        if (!this.requiresAddition()) {
            return true;
        }

        for (Slot slot : slots) {
            if (getAddition().test(slot.getItem())) return true;
        }
        return false;
    }

    default String getTemplateType() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack templateStack = ClientCompat.firstIngredientItem(getTemplate());
        if (templateStack.isEmpty()) {
            return this.getBase().getHoverName().getString();
        }

        if (minecraft.player == null || minecraft.level == null || templateStack.isEmpty()) {
            return templateStack.getHoverName().getString();
        }

        var tipCtx = Item.TooltipContext.of(minecraft.level);
        List<net.minecraft.network.chat.Component> lines = templateStack.getTooltipLines(tipCtx, minecraft.player, TooltipFlag.NORMAL);
        if (lines.size() > 1) {
            return lines.get(1).getString();
        }

        return templateStack.getHoverName().getString();
    }

    @Override
    default String getSearchString(BRBBookCategories.Category category) {
        return this.getTemplateType();
    }

    @Override
    default boolean usesItem(ItemStack item) {
        if (this.requiresTemplate() && this.getTemplate().test(item)) {
            return true;
        }
        if (this.getBase().getItem().equals(item.getItem())) {
            return true;
        }
        return this.requiresAddition() && this.getAddition().test(item);
    }
}
