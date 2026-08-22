/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 */
package mezz.jei.api.helpers;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public interface IGuiHelper {
    default public IDrawableStatic createDrawable(Identifier id, int u, int v, int width, int height) {
        return this.drawableBuilder(id, u, v, width, height).build();
    }

    public IDrawableBuilder drawableBuilder(Identifier var1, int var2, int var3, int var4, int var5);

    public IDrawableAnimated createAnimatedDrawable(IDrawableStatic var1, int var2, IDrawableAnimated.StartDirection var3, boolean var4);

    public IDrawableAnimated createAnimatedDrawable(IDrawableStatic var1, ITickTimer var2, IDrawableAnimated.StartDirection var3);

    public IDrawableStatic getSlotDrawable();

    public IDrawableStatic getOutputSlot();

    public IDrawableStatic getRecipeArrow();

    public IDrawableStatic getRecipeArrowFilled();

    public IDrawableAnimated createAnimatedRecipeArrow(int var1);

    public IDrawableStatic getRecipePlusSign();

    public IDrawableStatic getRecipeFlameFilled();

    public IDrawableStatic getRecipeFlameEmpty();

    public IDrawableAnimated createAnimatedRecipeFlame(int var1);

    public IDrawableStatic createBlankDrawable(int var1, int var2);

    default public IDrawable createDrawableItemStack(ItemStack ingredient) {
        return this.createDrawableIngredient(VanillaTypes.ITEM_STACK, ingredient);
    }

    default public IDrawable createDrawableItemLike(ItemLike itemLike) {
        return this.createDrawableIngredient(VanillaTypes.ITEM_STACK, itemLike.asItem().getDefaultInstance());
    }

    public <V> IDrawable createDrawableIngredient(IIngredientType<V> var1, V var2);

    public <V> IDrawable createDrawableIngredient(ITypedIngredient<V> var1);

    public ICraftingGridHelper createCraftingGridHelper();

    public IScrollBoxWidget createScrollBoxWidget(int var1, int var2, int var3, int var4);

    public ITickTimer createTickTimer(int var1, int var2, boolean var3);
}

