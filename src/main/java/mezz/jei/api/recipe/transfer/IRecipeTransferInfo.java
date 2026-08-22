/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.MenuType
 *  net.minecraft.world.inventory.Slot
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.recipe.transfer;

import java.util.List;
import java.util.Optional;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

public interface IRecipeTransferInfo<C extends AbstractContainerMenu, R> {
    public Class<? extends C> getContainerClass();

    public Optional<MenuType<C>> getMenuType();

    public IRecipeType<R> getRecipeType();

    public boolean canHandle(C var1, R var2);

    default public @Nullable IRecipeTransferError getHandlingError(C container, R recipe) {
        return null;
    }

    public List<Slot> getRecipeSlots(C var1, R var2);

    public List<Slot> getInventorySlots(C var1, R var2);

    default public boolean requireCompleteSets(C container, R recipe) {
        return true;
    }
}

