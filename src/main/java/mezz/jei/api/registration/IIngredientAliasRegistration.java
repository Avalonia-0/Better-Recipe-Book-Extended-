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
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

public interface IIngredientAliasRegistration {
    default public void addAlias(ItemStack itemStack, String alias) {
        this.addAlias(VanillaTypes.ITEM_STACK, itemStack, alias);
    }

    public <I> void addAlias(IIngredientType<I> var1, I var2, String var3);

    public <I> void addAlias(ITypedIngredient<I> var1, String var2);

    public <I> void addAliases(IIngredientType<I> var1, I var2, Collection<String> var3);

    public <I> void addAliases(ITypedIngredient<I> var1, Collection<String> var2);

    public <I> void addAliases(IIngredientType<I> var1, Collection<I> var2, String var3);

    public <I> void addAliases(Collection<ITypedIngredient<I>> var1, String var2);

    public <I> void addAliases(IIngredientType<I> var1, Collection<I> var2, Collection<String> var3);

    public <I> void addAliases(Collection<ITypedIngredient<I>> var1, Collection<String> var2);
}

