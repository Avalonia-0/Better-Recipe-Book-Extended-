/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponentPatch
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.Ingredient
 *  net.minecraft.world.item.crafting.display.SlotDisplay
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.material.Fluid
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.gui.builder;

import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

@ApiStatus.NonExtendable
public interface IIngredientAcceptor<THIS extends IIngredientAcceptor<THIS>> {
    public THIS add(SlotDisplay var1);

    default public THIS add(ItemStack itemStack) {
        return this.add(VanillaTypes.ITEM_STACK, itemStack);
    }

    default public THIS add(ItemLike itemLike) {
        return this.add(VanillaTypes.ITEM_STACK, itemLike.asItem().getDefaultInstance());
    }

    public THIS add(Fluid var1);

    public THIS add(Fluid var1, long var2);

    public THIS add(Fluid var1, long var2, DataComponentPatch var4);

    public THIS add(Ingredient var1);

    default public <I> THIS add(ITypedIngredient<I> typedIngredient) {
        return this.add(typedIngredient.getType(), typedIngredient.getIngredient());
    }

    public <I> THIS add(IIngredientType<I> var1, I var2);

    public <I> THIS addIngredients(IIngredientType<I> var1, List<@Nullable I> var2);

    public THIS addIngredientsUnsafe(List<?> var1);

    public THIS addTypedIngredients(List<ITypedIngredient<?>> var1);

    public THIS addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> var1);

    default public THIS addItemStacks(List<ItemStack> itemStacks) {
        return this.addIngredients(VanillaTypes.ITEM_STACK, itemStacks);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public <I> THIS addIngredient(IIngredientType<I> ingredientType, I ingredient) {
        return this.add(ingredientType, ingredient);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public THIS addIngredients(Ingredient ingredient) {
        return this.add(ingredient);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public <I> THIS addTypedIngredient(ITypedIngredient<I> typedIngredient) {
        return this.add(typedIngredient);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public THIS addItemStack(ItemStack itemStack) {
        return this.add(itemStack);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public THIS addItemLike(ItemLike itemLike) {
        return this.add(itemLike);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public THIS addFluidStack(Fluid fluid) {
        return this.add(fluid);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public THIS addFluidStack(Fluid fluid, long amount) {
        return this.add(fluid, amount);
    }

    @Deprecated(forRemoval=true, since="20.0.0")
    default public THIS addFluidStack(Fluid fluid, long amount, DataComponentPatch component) {
        return this.add(fluid, amount, component);
    }
}

