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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link IRecipeRegistration} implementation that records every
 *  {@code recipeType -> recipes} mapping reported by the loaded JEI plugins,
 *  without registering them into a JEI runtime. */
public final class RecipeCollector implements IRecipeRegistration {

    private static final Logger LOGGER = LogManager.getLogger("headless-jei");

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
    public ContextMap getContextMap() {
        return null;
    }

    @Override
    public <T> void addRecipes(IRecipeType<T> recipeType, List<T> recipes) {
        if (recipeType == null || recipes == null) return;
        LOGGER.info("[BRBE-JEI-Plugins] addRecipes type={} recipes={}",
                recipeType.getUid(), recipes == null ? -1 : recipes.size());
        List<Object> bucket = this.recipes.computeIfAbsent(recipeType, k -> new ArrayList<>());
        bucket.addAll(recipes);
        // Some mods only populate their JEI recipe source when the real JEI is
        // installed (they gate it on FabricLoader.isModLoaded("jei")), or their
        // server-synced recipe source is not ready at collection time, so their
        // registerRecipes hands us an empty list even though the recipes ARE
        // server-synced.  Fall back to the server-synced recipe registries
        // (fabric SynchronizedRecipes, plus the client RecipeManager which
        // covers wover/bclib-style syncs), matching the recipe type's registry
        // key against this JEI type's uid (namespace or full path).
        if (recipes.isEmpty()) {
            Identifier typeUid = recipeType.getUid();
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> all = new ArrayList<>();
            // 26.2: FabricRecipeAccess 已从 ClientPacketListener 移除
            // （recipes() 返回 RecipeAccess），旧 fallback 永远为空；
            // Internal.getClientSyncedRecipes() 在单人世界也被丢弃
            // （setClientSyncedRecipes 需要远程连接地址）。
            // 正确数据源：ClientRecipeSynchronizedEvent 回调直接给的
            // SynchronizedRecipes（BrbeJeiPlugins.syncedRecipes()，单机可用）。
            net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes synced =
                    com.alonie.brbe.jei.plugins.BrbeJeiPlugins.syncedRecipes();
            if (synced != null) {
                try {
                    java.util.Collection<? extends net.minecraft.world.item.crafting.RecipeHolder<?>> syncedRecipes =
                            synced.recipes();
                    if (syncedRecipes != null) all.addAll(syncedRecipes);
                } catch (Exception | LinkageError ignored) {
                    // SynchronizedRecipes accessor 差异兜底
                }
            }
            if (all.isEmpty()) {
                net.minecraft.world.item.crafting.RecipeMap syncedMap =
                        mezz.jei.common.Internal.getClientSyncedRecipes();
                if (syncedMap != null) {
                    try {
                        all.addAll(syncedMap.values());
                    } catch (Exception | LinkageError ignored) {
                        // RecipeMap accessor差异兜底
                    }
                }
            }
            if (all.isEmpty()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.getConnection() != null
                        && mc.getConnection().recipes() instanceof net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess fabricAccess) {
                    java.util.Collection<? extends net.minecraft.world.item.crafting.RecipeHolder<?>> syncedRecipes2 =
                            fabricAccess.getSynchronizedRecipes().recipes();
                    if (syncedRecipes2 != null) all.addAll(syncedRecipes2);
                }
            }
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : all) {
                try {
                    if (holder == null || holder.value() == null) continue;
                    Identifier typeKey =
                            net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
                    if (typeKey == null) continue;
                    if (typeKey.equals(typeUid)
                            || (typeKey.getNamespace().equals(typeUid.getNamespace())
                                && typeKey.getPath().equals(typeUid.getPath()))) {
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
}
