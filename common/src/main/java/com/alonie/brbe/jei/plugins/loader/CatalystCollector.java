package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.jei.plugins.stub.JeiHelpersStub;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@link IRecipeCatalystRegistration} implementation that records every
 *  {@code recipeType -> catalyst items} mapping reported by the loaded JEI
 *  plugins (workstation blocks of mod recipe types). */
public final class CatalystCollector implements IRecipeCatalystRegistration {

    /** JEI type uid → catalyst item ids. */
    private final Map<ResourceLocation, Set<ResourceLocation>> collected = new LinkedHashMap<>();

    public Map<ResourceLocation, Set<ResourceLocation>> collected() {
        return collected;
    }

    @Override
    public IIngredientManager getIngredientManager() {
        return null;
    }

    @Override
    public IJeiHelpers getJeiHelpers() {
        return JeiHelpersStub.INSTANCE;
    }

    @Override
    public void addRecipeCatalysts(RecipeType<?> recipeType, ItemLike... ingredients) {
        if (ingredients == null) return;
        for (ItemLike ingredient : ingredients) {
            if (ingredient != null) {
                add(recipeType, ingredient.asItem());
            }
        }
    }

    @Override
    public <T> void addRecipeCatalysts(RecipeType<?> recipeType, IIngredientType<T> ingredientType, List<T> ingredients) {
        if (ingredients == null) return;
        for (T ingredient : ingredients) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                add(recipeType, stack.getItem());
            }
        }
    }

    @Override
    public <T> void addRecipeCatalyst(IIngredientType<T> ingredientType, T ingredient, RecipeType<?>... recipeTypes) {
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            for (RecipeType<?> recipeType : recipeTypes) {
                add(recipeType, stack.getItem());
            }
        }
    }

    private void add(RecipeType<?> recipeType, Item item) {
        if (recipeType == null || item == null || recipeType.getUid() == null) return;
        ResourceLocation uid = recipeType.getUid();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return;
        collected.computeIfAbsent(uid, k -> new LinkedHashSet<>()).add(itemId);
    }
}
