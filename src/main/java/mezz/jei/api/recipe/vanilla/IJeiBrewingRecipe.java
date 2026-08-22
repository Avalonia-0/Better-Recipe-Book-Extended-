/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.api.recipe.vanilla;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Unmodifiable;

public interface IJeiBrewingRecipe {
    public @Unmodifiable List<ItemStack> getPotionInputs();

    public @Unmodifiable List<ItemStack> getIngredients();

    public ItemStack getPotionOutput();

    public int getBrewingSteps();

    public Identifier getUid();
}

