/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 */
package mezz.jei.api.runtime;

import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.ItemStack;

public interface IIngredientFilter {
    public void setFilterText(String var1);

    public String getFilterText();

    default public List<ItemStack> getFilteredItemStacks() {
        return this.getFilteredIngredients(VanillaTypes.ITEM_STACK);
    }

    public <T> List<T> getFilteredIngredients(IIngredientType<T> var1);
}

