/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.Holder
 *  net.minecraft.core.component.DataComponentPatch
 *  net.minecraft.world.level.material.Fluid
 */
package mezz.jei.api.helpers;

import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;

public interface IPlatformFluidHelper<T> {
    public IIngredientTypeWithSubtypes<Fluid, T> getFluidIngredientType();

    public T create(Holder<Fluid> var1, long var2, DataComponentPatch var4);

    public T create(Holder<Fluid> var1, long var2);

    public long bucketVolume();
}

