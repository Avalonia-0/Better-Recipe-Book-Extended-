/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponentPatch
 *  net.minecraft.world.level.material.Fluid
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 */
package mezz.jei.api.gui.builder;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.placement.IPlaceable;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface IRecipeSlotBuilder
extends IIngredientAcceptor<IRecipeSlotBuilder>,
IPlaceable<IRecipeSlotBuilder> {
    public IRecipeSlotBuilder addRichTooltipCallback(IRecipeSlotRichTooltipCallback var1);

    public IRecipeSlotBuilder setSlotName(String var1);

    public IRecipeSlotBuilder setStandardSlotBackground();

    public IRecipeSlotBuilder setOutputSlotBackground();

    public IRecipeSlotBuilder setBackground(IDrawable var1, int var2, int var3);

    public IRecipeSlotBuilder setOverlay(IDrawable var1, int var2, int var3);

    public IRecipeSlotBuilder setFluidRenderer(long var1, boolean var3, int var4, int var5);

    public <T> IRecipeSlotBuilder setCustomRenderer(IIngredientType<T> var1, IIngredientRenderer<T> var2);

    @Override
    @Deprecated(since="20.0.0", forRemoval=true)
    default public IRecipeSlotBuilder addFluidStack(Fluid fluid, long amount) {
        return (IRecipeSlotBuilder)this.add(fluid, amount);
    }

    @Override
    @Deprecated(since="20.0.0", forRemoval=true)
    default public IRecipeSlotBuilder addFluidStack(Fluid fluid, long amount, DataComponentPatch component) {
        return (IRecipeSlotBuilder)this.add(fluid, amount, component);
    }
}

