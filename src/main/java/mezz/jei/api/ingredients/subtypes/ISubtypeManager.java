/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.ingredients.subtypes;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface ISubtypeManager {
    default public @Nullable Object getSubtypeData(ItemStack ingredient, UidContext context) {
        return this.getSubtypeData(VanillaTypes.ITEM_STACK, ingredient, context);
    }

    public <T> @Nullable Object getSubtypeData(IIngredientTypeWithSubtypes<?, T> var1, T var2, UidContext var3);

    public <B, T> @Nullable Object getSubtypeData(IIngredientTypeWithSubtypes<B, T> var1, ITypedIngredient<T> var2, UidContext var3);

    default public boolean hasSubtypes(ItemStack ingredient) {
        return this.hasSubtypes(VanillaTypes.ITEM_STACK, ingredient);
    }

    public <T, B> boolean hasSubtypes(IIngredientTypeWithSubtypes<B, T> var1, T var2);
}

