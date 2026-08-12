package com.alonie.brbe.brewingstand;

import com.google.common.collect.Lists;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericRecipeBookCollection;
import com.alonie.brbe.generic.pins.Pinnable;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BrewingRecipeCollection extends GenericRecipeBookCollection<BrewableResult, BrewingStandMenu> implements Pinnable {
    private final BRBBookCategories.Category category;

    public BrewingRecipeCollection(List<BrewableResult> list, BrewingStandMenu menu, RegistryAccess registryAccess, BRBBookCategories.Category category) {
        super(list, menu, registryAccess);

        this.category = category;
    }

    public List<BrewableResult> getDisplayRecipes(boolean craftable) {
        List<BrewableResult> list = Lists.newArrayList();
        ItemStack carried = this.menu.getCarried();

        for (BrewableResult recipe : this.recipes) {
            if (recipe.hasMaterials(this.category, this.menu.slots, carried) == craftable) {
                list.add(recipe);
            }
        }

        return list;
    }

    @Override
    public boolean has(ResourceLocation resourceLocation) {
        for (BrewableResult recipe : this.recipes) {
            if (recipe.id().equals(resourceLocation)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean atleastOneCraftable(NonNullList<Slot> slots) {
        ItemStack carried = this.menu.getCarried();
        for (BrewableResult recipe : this.recipes) {
            if (recipe.hasMaterials(this.category, slots, carried)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean atleastOnePartiallyCraftable(NonNullList<Slot> slots) {
        return !getPartiallyCraftableRecipes(slots).isEmpty();
    }

    @Override
    public List<BrewableResult> getPartiallyCraftableRecipes(NonNullList<Slot> slots) {
        List<BrewableResult> partial = new ArrayList<>();
        ItemStack carried = this.menu.getCarried();
        for (BrewableResult recipe : this.recipes) {
            if (!recipe.hasMaterials(this.category, slots, carried)) {
                boolean hasIngredient = recipe.hasIngredient(slots, carried);
                boolean hasInput = recipe.hasInput(this.category, slots, carried);
                if ((hasIngredient || hasInput) && !(hasIngredient && hasInput)) {
                    partial.add(recipe);
                }
            }
        }
        return partial;
    }
}
