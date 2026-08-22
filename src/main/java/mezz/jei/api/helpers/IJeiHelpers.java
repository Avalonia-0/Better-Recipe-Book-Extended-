/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 */
package mezz.jei.api.helpers;

import java.util.Optional;
import java.util.stream.Stream;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import net.minecraft.resources.Identifier;

public interface IJeiHelpers {
    public IGuiHelper getGuiHelper();

    public IStackHelper getStackHelper();

    public IModIdHelper getModIdHelper();

    public IFocusFactory getFocusFactory();

    public IColorHelper getColorHelper();

    public IPlatformFluidHelper<?> getPlatformFluidHelper();

    public <T> Optional<IRecipeType<T>> getRecipeType(Identifier var1, Class<? extends T> var2);

    public Optional<IRecipeType<?>> getRecipeType(Identifier var1);

    public Stream<IRecipeType<?>> getAllRecipeTypes();

    public IIngredientManager getIngredientManager();

    public ICodecHelper getCodecHelper();

    public IVanillaRecipeFactory getVanillaRecipeFactory();

    public IIngredientVisibility getIngredientVisibility();
}

