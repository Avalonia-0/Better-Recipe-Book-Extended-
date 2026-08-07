package com.alonie.brbe.generic;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.pins.Pinnable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public interface GenericRecipe extends Pinnable {
    ResourceLocation id();

    @Override
    default boolean has(ResourceLocation identifier) {
        return (id().equals(identifier));
    }

    ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category);

    String getSearchString(BRBBookCategories.Category category);
}
