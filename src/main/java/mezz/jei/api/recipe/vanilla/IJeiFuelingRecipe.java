/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.recipe.vanilla;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public interface IJeiFuelingRecipe {
    public List<ItemStack> getInputs();

    public int getBurnTime();
}
