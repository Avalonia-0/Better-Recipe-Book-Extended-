/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Unmodifiable
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.recipe.vanilla;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

public interface IJeiGrindstoneRecipe {
    public @Unmodifiable List<ItemStack> getTopInputs();

    public @Unmodifiable List<ItemStack> getBottomInputs();

    public @Unmodifiable List<ItemStack> getOutputs();

    public int getMinXpReward();

    public int getMaxXpReward();

    public @Nullable Identifier getUid();

    public @Unmodifiable boolean isOutputRenderOnly();
}

