/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.Codec
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.crafting.RecipeHolder
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.recipe.category;

import com.mojang.serialization.Codec;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

public interface IRecipeCategory<T> {
    public IRecipeType<T> getRecipeType();

    public Component getTitle();

    public int getWidth();

    public int getHeight();

    public @Nullable IDrawable getIcon();

    public void setRecipe(IRecipeLayoutBuilder var1, T var2, IFocusGroup var3);

    default public void createRecipeExtras(IRecipeExtrasBuilder builder, T recipe, IFocusGroup focuses) {
    }

    default public void draw(T recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
    }

    default public void onDisplayedIngredientsUpdate(T recipe, List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
    }

    default public void getTooltip(ITooltipBuilder tooltip, T recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
    }

    default public boolean isHandled(T recipe) {
        return true;
    }

    default public @Nullable Identifier getIdentifier(T recipe) {
        if (recipe instanceof RecipeHolder) {
            RecipeHolder recipeHolder = (RecipeHolder)recipe;
            return recipeHolder.id().identifier();
        }
        return null;
    }

    @Deprecated(since="27.0.0", forRemoval=true)
    default public @Nullable Identifier getRegistryName(T recipe) {
        return this.getIdentifier(recipe);
    }

    default public Codec<T> getCodec(ICodecHelper codecHelper, IRecipeManager recipeManager) {
        IRecipeType<T> recipeType = this.getRecipeType();
        if (RecipeHolder.class.isAssignableFrom(recipeType.getRecipeClass())) {
            Codec recipeHolderCodec = codecHelper.getRecipeHolderCodec();
            return recipeHolderCodec;
        }
        return codecHelper.getSlowRecipeCategoryCodec(this, recipeManager);
    }

    default public boolean needsRecipeBorder() {
        return true;
    }
}

