package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.jei.plugins.stub.JeiHelpersStub;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link IRecipeRegistration} implementation that records every
 *  {@code recipeType -> recipes} mapping reported by the loaded JEI plugins,
 *  without registering them into a JEI runtime. */
public final class RecipeCollector implements IRecipeRegistration {

    private final Map<IRecipeType<?>, List<Object>> recipes = new LinkedHashMap<>();

    public Map<IRecipeType<?>, List<Object>> recipes() {
        return recipes;
    }

    @Override
    public IJeiHelpers getJeiHelpers() {
        return JeiHelpersStub.INSTANCE;
    }

    @Override
    public IIngredientManager getIngredientManager() {
        return null;
    }

    @Override
    public IVanillaRecipeFactory getVanillaRecipeFactory() {
        return null;
    }

    @Override
    public <T> void addRecipes(IRecipeType<T> recipeType, List<T> recipes) {
        if (recipeType == null || recipes == null) return;
        List<Object> bucket = this.recipes.computeIfAbsent(recipeType, k -> new ArrayList<>());
        bucket.addAll(recipes);
        // Some mods only populate their JEI recipe source when the real JEI is
        // installed (they gate it on FabricLoader.isModLoaded("jei")), so their
        // registerRecipes hands us an empty list here even though the recipes
        // ARE server-synced.  Fall back to the fabric recipe-sync registry,
        // matching the recipe type's registry key against this JEI type's uid.
        if (recipes.isEmpty()) {
            // 1.21.11's fabric-recipe-api (8.x) has no FabricRecipeAccess;
            // the headless core already captured the synchronized recipes into
            // Internal.getClientSyncedRecipes() via ClientRecipeSynchronizedEvent.
            net.minecraft.world.item.crafting.RecipeMap recipeMap = mezz.jei.common.Internal.getClientSyncedRecipes();
            if (recipeMap == null) return;
            Identifier typeUid = recipeType.getUid();
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : recipeMap.values()) {
                try {
                    if (holder == null || holder.value() == null) continue;
                    Identifier typeKey =
                            net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
                    if (typeKey == null) continue;
                    if (typeKey.equals(typeUid)) {
                        bucket.add(holder);
                    }
                } catch (Exception | LinkageError ignored) {
                    // one unmatched holder must not break the fallback
                }
            }
        }
    }

    @Override
    public <T> void addIngredientInfo(T ingredient, IIngredientType<T> ingredientType, Component... descriptionComponents) {
        // ingredient info pages are not recipes; ignored
    }

    @Override
    public <T> void addIngredientInfo(List<T> ingredients, IIngredientType<T> ingredientType, Component... descriptionComponents) {
        // ingredient info pages are not recipes; ignored
    }

    @Override
    public net.minecraft.util.context.ContextMap getContextMap() {
        return null;
    }
}
