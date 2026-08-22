/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.crafting.SmithingRecipe
 */
package mezz.jei.api.recipe.category.extensions.vanilla.smithing;

import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension;
import net.minecraft.world.item.crafting.SmithingRecipe;

public interface IExtendableSmithingRecipeCategory {
    public <R extends SmithingRecipe> void addExtension(Class<? extends R> var1, ISmithingCategoryExtension<R> var2);
}

