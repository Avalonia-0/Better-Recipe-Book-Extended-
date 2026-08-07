package com.alonie.brbe.generic;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.pins.Pinnable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public interface GenericRecipe extends Pinnable {
    Identifier id();

    @Override
    default boolean has(Identifier identifier) {
        return (id().equals(identifier));
    }

    ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category);

    String getSearchString(BRBBookCategories.Category category);

    /**
     * Whether this recipe consumes {@code item} as one of its ingredients.
     * Used by {@code partialOnlyWhenCarrying}: a partial recipe is only shown
     * while the player is holding one of its materials.
     */
    default boolean usesItem(ItemStack item) {
        return false;
    }
}
