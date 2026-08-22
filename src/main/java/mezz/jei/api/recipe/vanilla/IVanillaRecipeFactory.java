/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.CraftingBookCategory
 *  net.minecraft.world.item.crafting.display.SlotDisplay
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.recipe.vanilla;

import java.util.List;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.api.recipe.vanilla.IJeiShapedRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

public interface IVanillaRecipeFactory {
    public IJeiAnvilRecipe createAnvilRecipe(ItemStack var1, List<ItemStack> var2, List<ItemStack> var3, @Nullable Identifier var4);

    public IJeiAnvilRecipe createAnvilRecipe(List<ItemStack> var1, List<ItemStack> var2, List<ItemStack> var3, Identifier var4);

    public IJeiGrindstoneRecipe createGrindstoneRecipe(List<ItemStack> var1, List<ItemStack> var2, List<ItemStack> var3, int var4, int var5, Identifier var6);

    public IJeiBrewingRecipe createBrewingRecipe(List<ItemStack> var1, ItemStack var2, ItemStack var3, Identifier var4);

    public IJeiBrewingRecipe createBrewingRecipe(List<ItemStack> var1, List<ItemStack> var2, ItemStack var3, Identifier var4);

    public IJeiShapedRecipeBuilder createShapedRecipeBuilder(CraftingBookCategory var1, SlotDisplay var2);
}

