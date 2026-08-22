/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.Codec
 */
package mezz.jei.api.registration;

import com.mojang.serialization.Codec;
import java.util.Collection;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.ISubtypeManager;

public interface IModIngredientRegistration {
    public ISubtypeManager getSubtypeManager();

    public IColorHelper getColorHelper();

    public <V> void register(IIngredientType<V> var1, Collection<V> var2, IIngredientHelper<V> var3, IIngredientRenderer<V> var4, Codec<V> var5);
}

