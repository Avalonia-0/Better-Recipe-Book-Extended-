/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.recipe.vanilla;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public interface IJeiCompostingRecipe {
    public List<ItemStack> getInputs();

    public float getChance();

    public Identifier getUid();
}
