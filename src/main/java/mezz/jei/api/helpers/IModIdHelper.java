/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 */
package mezz.jei.api.helpers;

import java.util.Optional;
import java.util.Set;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.Component;

public interface IModIdHelper {
    public String getModNameForModId(String var1);

    public boolean isDisplayingModNameEnabled();

    public String getFormattedModNameForModId(String var1);

    public Set<String> getModAliases(String var1);

    public <T> Optional<Component> getModNameForTooltip(ITypedIngredient<T> var1);
}

