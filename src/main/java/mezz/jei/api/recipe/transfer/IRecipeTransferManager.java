/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.inventory.AbstractContainerMenu
 */
package mezz.jei.api.recipe.transfer;

import java.util.Optional;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;

public interface IRecipeTransferManager {
    public <C extends AbstractContainerMenu, R> Optional<IRecipeTransferHandler<C, R>> getRecipeTransferHandler(C var1, IRecipeCategory<R> var2);
}

