package com.alonie.brbe.smithingtable;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericGhostRecipe;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import com.alonie.brbe.recipe.smithing.BRBSmithingTransformRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class SmithingGhostRecipe extends GenericGhostRecipe<BRBSmithingRecipe> {
    public SmithingGhostRecipe(@Nullable Consumer<ItemStack> onGhostUpdate, RegistryAccess registryAccess) {
        super(onGhostUpdate, registryAccess);
    }

    @Override
    public ItemStack getCurrentResult(BRBBookCategories.Category category) {
        if (this.recipe == null) {
            return ItemStack.EMPTY;
        }

        if (this.recipe instanceof BRBSmithingTransformRecipe) {
            return this.recipe.getResult(this.registryAccess, category);
        }

        ItemStack materialStack = this.ingredients.isEmpty() ? ItemStack.EMPTY : this.ingredients.get(0).getItem();
        if (materialStack.is(Items.QUARTZ)) return this.recipe.getResult(TrimMaterials.QUARTZ, this.registryAccess, category);
        if (materialStack.is(Items.IRON_INGOT)) return this.recipe.getResult(TrimMaterials.IRON, this.registryAccess, category);
        if (materialStack.is(Items.NETHERITE_INGOT)) return this.recipe.getResult(TrimMaterials.NETHERITE, this.registryAccess, category);
        if (materialStack.is(Items.COPPER_INGOT)) return this.recipe.getResult(TrimMaterials.COPPER, this.registryAccess, category);
        if (materialStack.is(Items.GOLD_INGOT)) return this.recipe.getResult(TrimMaterials.GOLD, this.registryAccess, category);
        if (materialStack.is(Items.EMERALD)) return this.recipe.getResult(TrimMaterials.EMERALD, this.registryAccess, category);
        if (materialStack.is(Items.DIAMOND)) return this.recipe.getResult(TrimMaterials.DIAMOND, this.registryAccess, category);
        if (materialStack.is(Items.LAPIS_LAZULI)) return this.recipe.getResult(TrimMaterials.LAPIS, this.registryAccess, category);
        if (materialStack.is(Items.AMETHYST_SHARD)) return this.recipe.getResult(TrimMaterials.AMETHYST, this.registryAccess, category);
        if (materialStack.is(Items.RESIN_BRICK)) return this.recipe.getResult(TrimMaterials.RESIN, this.registryAccess, category);
        return this.recipe.getResult(TrimMaterials.REDSTONE, this.registryAccess, category);
    }
}
