package com.alonie.brbe.brewingstand;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericRecipe;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.*;

public class BrewableResult implements GenericRecipe {
    public Object recipe;
    public Identifier input;

    public BrewableResult(Object recipe) {
        this.recipe = recipe;
        this.input = BuiltInRegistries.POTION.getKey(getFrom(recipe));
    }

    public boolean hasIngredient(List<Slot> slots, ItemStack carried) {
        for (ItemStack itemStack : ClientCompat.ingredientItems(getIngredient(recipe))) {
            if (!carried.isEmpty() && itemStack.getItem().equals(carried.getItem())) return true;
            for (Slot slot : slots) {
                if (itemStack.getItem().equals(slot.getItem().getItem())) return true;
            }
        }
        return false;
    }

    public ItemStack inputAsItemStack(BRBBookCategories.Category category) {
        Potion inputPotion = getFrom(recipe);

        var potionItem = category.getItemIcons().getFirst().getItem();
        return potionStackFromPotion(potionItem, inputPotion);
    }

    public boolean hasInput(BRBBookCategories.Category category, List<Slot> slots, ItemStack carried) {
        ItemStack inputStack = inputAsItemStack(category);

        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(inputStack, carried))
            return true;

        for (Slot slot : slots) {
            ItemStack itemStack = slot.getItem();

            if (ItemStack.isSameItemSameComponents(inputStack, itemStack))
                return true;
        }

        return false;
    }

    public boolean hasMaterials(BRBBookCategories.Category category, List<Slot> slots, ItemStack carried) {
        boolean hasIngredient = hasIngredient(slots, carried);
        boolean hasInput = hasInput(category, slots, carried);

        return hasIngredient && hasInput;
    }

    public boolean hasPartialMaterials(BRBBookCategories.Category category, List<Slot> slots, ItemStack carried) {
        return hasIngredient(slots, carried) || hasInput(category, slots, carried);
    }

    @Override
    public Identifier id() {
        return BuiltInRegistries.POTION.getKey(getTo(recipe));
    }

    public Component getHoverName(BRBBookCategories.Category category) {
        var resultPotion = getTo(recipe);
        var potionItem = category.getItemIcons().getFirst().getItem();
        return potionStackFromPotion(potionItem, resultPotion).getHoverName();
    }

    @Override
    public ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category) {
        var resultPotion = getTo(recipe);
        var potionItem = category.getItemIcons().getFirst().getItem();
        return potionStackFromPotion(potionItem, resultPotion);
    }

    @Override
    public String getSearchString(BRBBookCategories.Category category) {
        return getHoverName(category).getString();
    }

    public static ItemStack potionStackFromPotion(Item item, Potion pot) {
        return PotionContents.createItemStack(item, BuiltInRegistries.POTION.wrapAsHolder(pot));
    }
}
