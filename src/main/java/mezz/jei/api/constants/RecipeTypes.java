/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.crafting.BlastingRecipe
 *  net.minecraft.world.item.crafting.CampfireCookingRecipe
 *  net.minecraft.world.item.crafting.CraftingRecipe
 *  net.minecraft.world.item.crafting.RecipeType
 *  net.minecraft.world.item.crafting.SmeltingRecipe
 *  net.minecraft.world.item.crafting.SmithingRecipe
 *  net.minecraft.world.item.crafting.SmokingRecipe
 *  net.minecraft.world.item.crafting.StonecutterRecipe
 */
package mezz.jei.api.constants;

import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiCompostingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

public final class RecipeTypes {
    public static final IRecipeHolderType<CraftingRecipe> CRAFTING = IRecipeHolderType.create(RecipeType.CRAFTING);
    public static final IRecipeHolderType<StonecutterRecipe> STONECUTTING = IRecipeHolderType.create(RecipeType.STONECUTTING);
    public static final IRecipeHolderType<SmeltingRecipe> SMELTING = IRecipeHolderType.create(RecipeType.SMELTING);
    public static final IRecipeHolderType<SmokingRecipe> SMOKING = IRecipeHolderType.create(RecipeType.SMOKING);
    public static final IRecipeHolderType<BlastingRecipe> BLASTING = IRecipeHolderType.create(RecipeType.BLASTING);
    public static final IRecipeHolderType<CampfireCookingRecipe> CAMPFIRE_COOKING = IRecipeHolderType.create(RecipeType.CAMPFIRE_COOKING);
    public static final IRecipeType<IJeiFuelingRecipe> SMELTING_FUEL = IRecipeType.create("minecraft", "smelting_fuel", IJeiFuelingRecipe.class);
    public static final IRecipeType<IJeiFuelingRecipe> BLASTING_FUEL = IRecipeType.create("minecraft", "blasting_fuel", IJeiFuelingRecipe.class);
    public static final IRecipeType<IJeiFuelingRecipe> SMOKING_FUEL = IRecipeType.create("minecraft", "smoking_fuel", IJeiFuelingRecipe.class);
    public static final IRecipeType<IJeiBrewingRecipe> BREWING = IRecipeType.create("minecraft", "brewing", IJeiBrewingRecipe.class);
    public static final IRecipeType<IJeiAnvilRecipe> ANVIL = IRecipeType.create("minecraft", "anvil", IJeiAnvilRecipe.class);
    public static final IRecipeType<IJeiGrindstoneRecipe> GRINDSTONE = IRecipeType.create("minecraft", "grindstone", IJeiGrindstoneRecipe.class);
    public static final IRecipeHolderType<SmithingRecipe> SMITHING = IRecipeHolderType.create(RecipeType.SMITHING);
    public static final IRecipeType<IJeiCompostingRecipe> COMPOSTING = IRecipeType.create("minecraft", "compostable", IJeiCompostingRecipe.class);
    public static final IRecipeType<IJeiIngredientInfoRecipe> INFORMATION = IRecipeType.create("jei", "information", IJeiIngredientInfoRecipe.class);

    private RecipeTypes() {
    }
}

