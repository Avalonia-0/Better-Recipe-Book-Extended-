/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.MenuType
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.registration;

import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jspecify.annotations.Nullable;

public interface IRecipeTransferRegistration {
    public IJeiHelpers getJeiHelpers();

    public IRecipeTransferHandlerHelper getTransferHelper();

    public <C extends AbstractContainerMenu, R> void addRecipeTransferHandler(Class<? extends C> var1, @Nullable MenuType<C> var2, IRecipeType<R> var3, int var4, int var5, int var6, int var7);

    public <C extends AbstractContainerMenu, R> void addRecipeTransferHandler(IRecipeTransferInfo<C, R> var1);

    public <C extends AbstractContainerMenu, R> void addRecipeTransferHandler(IRecipeTransferHandler<C, R> var1, IRecipeType<R> var2);

    public <C extends AbstractContainerMenu> void addUniversalRecipeTransferHandler(IUniversalRecipeTransferHandler<C> var1);
}

