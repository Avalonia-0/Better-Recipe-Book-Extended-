/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 */
package mezz.jei.api.recipe;

import java.util.List;
import java.util.stream.Stream;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;

public interface IFocusGroup {
    public boolean isEmpty();

    public List<IFocus<?>> getAllFocuses();

    public Stream<IFocus<?>> getFocuses(RecipeIngredientRole var1);

    public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> var1);

    public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> var1, RecipeIngredientRole var2);

    default public Stream<IFocus<ItemStack>> getItemStackFocuses() {
        return this.getFocuses(VanillaTypes.ITEM_STACK);
    }

    default public Stream<IFocus<ItemStack>> getItemStackFocuses(RecipeIngredientRole role) {
        return this.getFocuses(VanillaTypes.ITEM_STACK, role);
    }
}

