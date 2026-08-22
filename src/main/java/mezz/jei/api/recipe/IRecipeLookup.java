/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.recipe;

import java.util.Collection;
import java.util.stream.Stream;
import mezz.jei.api.recipe.IFocus;

public interface IRecipeLookup<R> {
    public IRecipeLookup<R> limitFocus(Collection<? extends IFocus<?>> var1);

    public IRecipeLookup<R> includeHidden();

    public Stream<R> get();
}

