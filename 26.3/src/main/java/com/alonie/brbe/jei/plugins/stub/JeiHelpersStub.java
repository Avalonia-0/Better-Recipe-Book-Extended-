package com.alonie.brbe.jei.plugins.stub;

import com.mojang.serialization.Codec;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Minimal {@link IJeiHelpers}: provides a {@link GuiHelperStub} (the one helper
 * plugin category constructors actually need), and null/empty for the rest.
 * BRBE only reads the recipes a plugin exposes via {@code setRecipe}.
 */
public final class JeiHelpersStub implements IJeiHelpers {

    public static final JeiHelpersStub INSTANCE = new JeiHelpersStub();

    private JeiHelpersStub() {}

    @Override
    public IGuiHelper getGuiHelper() {
        return GuiHelperStub.INSTANCE;
    }

    @Override
    public IStackHelper getStackHelper() {
        return null;
    }

    @Override
    public IModIdHelper getModIdHelper() {
        return null;
    }

    @Override
    public IFocusFactory getFocusFactory() {
        return null;
    }

    @Override
    public IColorHelper getColorHelper() {
        return null;
    }

    @Override
    public IPlatformFluidHelper<?> getPlatformFluidHelper() {
        return null;
    }

    @Override
    public <T> Optional<IRecipeType<T>> getRecipeType(Identifier uid, Class<? extends T> recipeClass) {
        return Optional.empty();
    }

    @Override
    public Optional<IRecipeType<?>> getRecipeType(Identifier uid) {
        return Optional.empty();
    }

    @Override
    public Stream<IRecipeType<?>> getAllRecipeTypes() {
        return Stream.empty();
    }

    @Override
    public IIngredientManager getIngredientManager() {
        return null;
    }

    @Override
    public ICodecHelper getCodecHelper() {
        return null;
    }

    @Override
    public IVanillaRecipeFactory getVanillaRecipeFactory() {
        return null;
    }

    @Override
    public IIngredientVisibility getIngredientVisibility() {
        return null;
    }
}
