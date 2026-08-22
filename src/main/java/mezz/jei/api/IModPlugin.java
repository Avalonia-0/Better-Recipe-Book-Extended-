/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 */
package mezz.jei.api;

import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import mezz.jei.api.registration.IModInfoRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IRuntimeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.config.IJeiConfigManager;
import net.minecraft.resources.Identifier;

public interface IModPlugin {
    public Identifier getPluginUid();

    default public void registerItemSubtypes(ISubtypeRegistration registration) {
    }

    default public <T> void registerFluidSubtypes(ISubtypeRegistration registration, IPlatformFluidHelper<T> platformFluidHelper) {
    }

    default public void registerIngredients(IModIngredientRegistration registration) {
    }

    default public void registerExtraIngredients(IExtraIngredientRegistration registration) {
    }

    default public void registerIngredientAliases(IIngredientAliasRegistration registration) {
    }

    default public void registerModInfo(IModInfoRegistration modAliasRegistration) {
    }

    default public void registerCategories(IRecipeCategoryRegistration registration) {
    }

    default public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
    }

    default public void registerRecipes(IRecipeRegistration registration) {
    }

    default public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
    }

    default public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
    }

    default public void registerGuiHandlers(IGuiHandlerRegistration registration) {
    }

    default public void registerAdvanced(IAdvancedRegistration registration) {
    }

    default public void registerRuntime(IRuntimeRegistration registration) {
    }

    default public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
    }

    default public void onRuntimeUnavailable() {
    }

    default public void onConfigManagerAvailable(IJeiConfigManager configManager) {
    }
}

