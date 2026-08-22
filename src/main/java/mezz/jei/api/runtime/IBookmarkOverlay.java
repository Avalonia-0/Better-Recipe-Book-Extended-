/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.runtime;

import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface IBookmarkOverlay {
    public Optional<ITypedIngredient<?>> getIngredientUnderMouse();

    public <T> @Nullable T getIngredientUnderMouse(IIngredientType<T> var1);

    default public @Nullable ItemStack getItemStackUnderMouse() {
        return (ItemStack)this.getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
    }
}

