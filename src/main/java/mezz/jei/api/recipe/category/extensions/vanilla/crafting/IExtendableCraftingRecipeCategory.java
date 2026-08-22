/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.crafting.CraftingRecipe
 */
package mezz.jei.api.recipe.category.extensions.vanilla.crafting;

import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.crafting.CraftingRecipe;

public interface IExtendableCraftingRecipeCategory {
    public <R extends CraftingRecipe> void addExtension(Class<? extends R> var1, ICraftingCategoryExtension<R> var2);
}

