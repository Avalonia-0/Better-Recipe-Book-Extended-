/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.runtime;

import java.util.Set;
import mezz.jei.api.ingredients.ITypedIngredient;

public interface IEditModeConfig {
    public <V> boolean isIngredientHiddenUsingConfigFile(ITypedIngredient<V> var1);

    public <V> Set<HideMode> getIngredientHiddenUsingConfigFile(ITypedIngredient<V> var1);

    public <V> void hideIngredientUsingConfigFile(ITypedIngredient<V> var1, HideMode var2);

    public <V> void showIngredientUsingConfigFile(ITypedIngredient<V> var1, HideMode var2);

    public static enum HideMode {
        SINGLE,
        WILDCARD;

    }
}

