package com.alonie.brbe.smithingtable;

import com.google.common.collect.Lists;
import com.alonie.brbe.generic.GenericRecipeBookCollection;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SmithingRecipeCollection extends GenericRecipeBookCollection<BRBSmithingRecipe, SmithingMenu> {
    public SmithingRecipeCollection(List<? extends BRBSmithingRecipe> list, SmithingMenu menu, RegistryAccess registryAccess) {
        super(list, menu, registryAccess);
    }

    public List<BRBSmithingRecipe> getDisplayRecipes(boolean craftable) {
        List<BRBSmithingRecipe> list = Lists.newArrayList();
        ItemStack carried = this.menu.getCarried();

        for (BRBSmithingRecipe recipe : this.recipes) {
            if (recipe.hasMaterials(this.menu.slots, registryAccess, carried) == craftable) {
                list.add(recipe);
            }
        }

        return list;
    }

    @Override
    public boolean atleastOneCraftable(NonNullList<Slot> slots) {
        ItemStack carried = this.menu.getCarried();
        for (BRBSmithingRecipe recipe : this.recipes) {
            if (recipe.hasMaterials(slots, registryAccess, carried)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isCraftable(BRBSmithingRecipe recipe, NonNullList<Slot> slots) {
        return recipe.hasMaterials(slots, registryAccess, this.menu.getCarried());
    }

    @Override
    public boolean atleastOnePartiallyCraftable(NonNullList<Slot> slots) {
        return !getPartiallyCraftableRecipes(slots).isEmpty();
    }

    @Override
    public List<BRBSmithingRecipe> getPartiallyCraftableRecipes(NonNullList<Slot> slots) {
        List<BRBSmithingRecipe> list = Lists.newArrayList();
        ItemStack carried = this.menu.getCarried();

        for (BRBSmithingRecipe recipe : this.recipes) {
            if (!recipe.hasMaterials(slots, registryAccess, carried) && recipe.hasPartialMaterials(slots, registryAccess, carried)) {
                list.add(recipe);
            }
        }

        return list;
    }
}
