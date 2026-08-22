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

public interface IJeiAnvilRecipe {
    public @Unmodifiable List<ItemStack> getLeftInputs();

    public @Unmodifiable List<ItemStack> getRightInputs();

    public @Unmodifiable List<ItemStack> getOutputs();

    public @Nullable Identifier getUid();
}

