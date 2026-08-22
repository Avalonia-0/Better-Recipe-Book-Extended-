/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 *  org.jetbrains.annotations.Unmodifiable
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.gui.ingredient;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

@ApiStatus.NonExtendable
public interface IRecipeSlotView {
    public Stream<ITypedIngredient<?>> getAllIngredients();

    public @Unmodifiable List<@Nullable ITypedIngredient<?>> getAllIngredientsList();

    public Optional<ITypedIngredient<?>> getDisplayedIngredient();

    public RecipeIngredientRole getRole();

    public void drawHighlight(GuiGraphics var1, int var2);

    default public <T> Stream<T> getIngredients(IIngredientType<T> ingredientType) {
        return this.getAllIngredients().map(i -> i.getIngredient(ingredientType)).flatMap(Optional::stream);
    }

    default public Stream<ItemStack> getItemStacks() {
        return this.getIngredients(VanillaTypes.ITEM_STACK);
    }

    default public boolean isEmpty() {
        return this.getAllIngredients().findAny().isEmpty();
    }

    default public Optional<ItemStack> getDisplayedItemStack() {
        return this.getDisplayedIngredient(VanillaTypes.ITEM_STACK);
    }

    default public <T> Optional<T> getDisplayedIngredient(IIngredientType<T> ingredientType) {
        return this.getDisplayedIngredient().flatMap(i -> i.getIngredient(ingredientType));
    }

    public Optional<String> getSlotName();
}

