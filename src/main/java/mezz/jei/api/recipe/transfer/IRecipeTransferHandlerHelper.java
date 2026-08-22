/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.MenuType
 *  net.minecraft.world.item.crafting.CraftingRecipe
 *  net.minecraft.world.item.crafting.RecipeHolder
 *  net.minecraft.world.item.crafting.display.SlotDisplay
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.recipe.transfer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

public interface IRecipeTransferHandlerHelper {
    public IRecipeTransferError createInternalError();

    public IRecipeTransferError createUserErrorWithTooltip(Component var1);

    public IRecipeTransferError createUserErrorForMissingSlots(Component var1, Collection<IRecipeSlotView> var2);

    public <C extends AbstractContainerMenu, R> IRecipeTransferInfo<C, R> createBasicRecipeTransferInfo(Class<? extends C> var1, @Nullable MenuType<C> var2, IRecipeType<R> var3, int var4, int var5, int var6, int var7);

    public <C extends AbstractContainerMenu, R> IRecipeTransferHandler<C, R> createUnregisteredRecipeTransferHandler(IRecipeTransferInfo<C, R> var1);

    public IRecipeSlotsView createRecipeSlotsView(List<IRecipeSlotView> var1);

    public boolean recipeTransferHasServerSupport();

    public Map<Integer, SlotDisplay> getGuiSlotIndexToIngredientMap(RecipeHolder<CraftingRecipe> var1);
}

