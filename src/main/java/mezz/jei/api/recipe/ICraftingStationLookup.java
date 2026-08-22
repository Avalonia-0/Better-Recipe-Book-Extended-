/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 */
package mezz.jei.api.recipe;

import java.util.stream.Stream;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

public interface ICraftingStationLookup {
    public ICraftingStationLookup includeHidden();

    public Stream<ITypedIngredient<?>> get();

    public <S> Stream<S> get(IIngredientType<S> var1);

    default public Stream<ItemStack> getItemStack() {
        return this.get(VanillaTypes.ITEM_STACK);
    }
}

