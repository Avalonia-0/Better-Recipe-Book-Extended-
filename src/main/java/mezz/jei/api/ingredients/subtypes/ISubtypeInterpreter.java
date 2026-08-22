/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.ingredients.subtypes;

import mezz.jei.api.ingredients.subtypes.UidContext;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ISubtypeInterpreter<T> {
    public @Nullable Object getSubtypeData(T var1, UidContext var2);
}

