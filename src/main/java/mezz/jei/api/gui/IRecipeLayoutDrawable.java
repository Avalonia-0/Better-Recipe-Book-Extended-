/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.renderer.Rect2i
 *  net.minecraft.world.item.ItemStack
 */
package mezz.jei.api.gui;

import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

public interface IRecipeLayoutDrawable<R> {
    public void setPosition(int var1, int var2);

    public void drawRecipe(GuiGraphics var1, int var2, int var3);

    public void drawOverlays(GuiGraphics var1, int var2, int var3);

    public boolean isMouseOver(double var1, double var3);

    default public Optional<ItemStack> getItemStackUnderMouse(int mouseX, int mouseY) {
        return this.getIngredientUnderMouse(mouseX, mouseY, VanillaTypes.ITEM_STACK);
    }

    public <T> Optional<T> getIngredientUnderMouse(int var1, int var2, IIngredientType<T> var3);

    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double var1, double var3);

    public Rect2i getRect();

    public Rect2i getRectWithBorder();

    default public Rect2i getRecipeTransferButtonArea() {
        return this.getSideButtonArea(0);
    }

    default public Rect2i getRecipeBookmarkButtonArea() {
        return this.getSideButtonArea(1);
    }

    public Rect2i getSideButtonArea(int var1);

    public IRecipeSlotsView getRecipeSlotsView();

    public IRecipeCategory<R> getRecipeCategory();

    public R getRecipe();

    public IJeiInputHandler getInputHandler();

    public void tick();
}

