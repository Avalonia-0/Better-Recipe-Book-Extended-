/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponentType
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 */
package mezz.jei.api.registration;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface ISubtypeRegistration {
    public <B, I> void registerSubtypeInterpreter(IIngredientTypeWithSubtypes<B, I> var1, B var2, ISubtypeInterpreter<I> var3);

    default public void registerSubtypeInterpreter(Item item, ISubtypeInterpreter<ItemStack> interpreter) {
        this.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, item, interpreter);
    }

    public void registerFromDataComponentTypes(Item var1, DataComponentType<?> ... var2);
}

