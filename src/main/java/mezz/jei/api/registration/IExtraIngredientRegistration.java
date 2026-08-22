/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 */
package mezz.jei.api.registration;

import java.util.Collection;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.ItemStack;

public interface IExtraIngredientRegistration {
    default public void addExtraItemStacks(Collection<ItemStack> extraItemStacks) {
        this.addExtraIngredients(VanillaTypes.ITEM_STACK, extraItemStacks);
    }

    public <V> void addExtraIngredients(IIngredientType<V> var1, Collection<V> var2);
}

