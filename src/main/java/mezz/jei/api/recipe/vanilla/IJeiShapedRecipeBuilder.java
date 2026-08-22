/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.crafting.CraftingRecipe
 *  net.minecraft.world.item.crafting.Ingredient
 *  net.minecraft.world.item.crafting.display.SlotDisplay
 */
package mezz.jei.api.recipe.vanilla;

import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

public interface IJeiShapedRecipeBuilder {
    public IJeiShapedRecipeBuilder define(Character var1, Ingredient var2);

    public IJeiShapedRecipeBuilder define(Character var1, Ingredient var2, SlotDisplay var3);

    public IJeiShapedRecipeBuilder pattern(String var1);

    public IJeiShapedRecipeBuilder group(String var1);

    public CraftingRecipe build();
}

