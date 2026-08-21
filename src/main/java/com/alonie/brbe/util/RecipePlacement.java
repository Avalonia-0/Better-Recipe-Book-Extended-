package com.alonie.brbe.util;

import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

public class RecipePlacement {

    protected List<List<Ingredient>> placement = new ArrayList<>();

    public RecipePlacement(int size) {
        for (int i = 0; i < size; i++) placement.add(new ArrayList<>());
    }

    public static List<List<Ingredient>> create(RecipeHolder<?> recipe, int gridWidth, int gridHeight) {
        RecipePlacement placer = new RecipePlacement(gridWidth * gridHeight);
        PlaceRecipeHelper.Output<Ingredient> output = new PlaceRecipeHelper.Output<Ingredient>() {
            @Override
            public void addItemToSlot(Ingredient ingredient, int slot, int x, int y) {
                if (!ingredient.isEmpty() && slot < placer.placement.size()) {
                    placer.placement.get(slot).add(ingredient);
                }
            }
        };
        PlaceRecipeHelper.placeRecipe(gridWidth, gridHeight, recipe.value(),
                recipe.value().placementInfo().ingredients(), output);
        return placer.getPlacement();
    }

    public List<List<Ingredient>> getPlacement() {
        return placement;
    }
}
