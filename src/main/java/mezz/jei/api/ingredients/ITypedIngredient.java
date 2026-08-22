/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.ingredients;

import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

@ApiStatus.NonExtendable
public interface ITypedIngredient<T> {
    public IIngredientType<T> getType();

    public T getIngredient();

    default public <V> Optional<V> getIngredient(IIngredientType<V> ingredientType) {
        return ingredientType.castIngredient(this.getIngredient());
    }

    default public <V> @Nullable V getCastIngredient(IIngredientType<V> ingredientType) {
        return ingredientType.getCastIngredient(this.getIngredient());
    }

    default public <V> @Nullable ITypedIngredient<V> cast(IIngredientType<V> ingredientType) {
        if (this.getType().equals(ingredientType)) {
            ITypedIngredient cast = this;
            return cast;
        }
        return null;
    }

    default public @Nullable ITypedIngredient<ItemStack> castToItemStackType() {
        return this.cast(VanillaTypes.ITEM_STACK);
    }

    default public <B> B getBaseIngredient(IIngredientTypeWithSubtypes<B, T> ingredientType) {
        return ingredientType.getBase(this.getIngredient());
    }

    default public Optional<ItemStack> getItemStack() {
        return this.getIngredient(VanillaTypes.ITEM_STACK);
    }
}

