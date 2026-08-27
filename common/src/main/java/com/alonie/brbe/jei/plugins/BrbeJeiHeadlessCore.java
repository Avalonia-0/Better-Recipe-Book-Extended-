package com.alonie.brbe.jei.plugins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge;
import com.alonie.brbe.jei.plugins.loader.BrbeJeiPluginFinder;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.common.Internal;
import mezz.jei.library.plugins.jei.JeiInternalPlugin;
import mezz.jei.library.plugins.vanilla.VanillaPlugin;
import mezz.jei.library.startup.JeiStarter;
import mezz.jei.library.startup.StartData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.21.1 无头 JEI 核心：真实 JEI 缺席时启动内嵌全量 JEI 运行时（官方 1.21.1
 * 源码内嵌的 api/common/library），仅喂给 BRBE 自身的查询 viewer——不注册 JEI
 * GUI、不注册键位，玩家看不到任何 JEI 功能。
 *
 * <p>对应 1.21.11 的 BrbeJeiHeadlessCore。差异：1.21.1 无 fabric 事件包与
 * RecipeMap（Internal.getClientSyncedRecipes 返回 List&lt;RecipeHolder&lt;?&gt;&gt;），
 * 平台事件由 fabric/neoforge 入口调用 {@link #start()} / {@link #stop()} /
 * {@link #onClientStopping()}；真实 JEI 存在时（mod id {@code jei} &lt;
 * {@code zzzbrbe}）其类覆盖内嵌类且本核心不启动。
 */
public final class BrbeJeiHeadlessCore {

    private BrbeJeiHeadlessCore() {}

    private static JeiStarter jeiStarter;
    private static volatile boolean running;

    /** Recipe types already injected into the headless JEI recipe manager this
     *  server join (dedupe guard: addRecipes appends). Cleared by stop(). */
    private static final Set<ResourceLocation> injectedTypes = new HashSet<>();

    /** Public entry (platform client entrypoints): start the embedded core if
     *  a level is up and real JEI is absent.  Idempotent (running guard). */
    public static void start() {
        if (running) {
            return;
        }
        if (BrbeJeiPlatform.realJeiLoaded()) {
            // real JEI registers before BRBE (jei < zzzbrbe) and shadows the
            // embedded classes; starting a second core would be a no-op anyway.
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] no level yet; deferring embedded core start");
            return;
        }
        try {
            Internal.setServerConnection(new HeadlessConnectionToServer());
            Internal.setKeyMappings(new HeadlessKeyMappings());

            List<IModPlugin> plugins = new ArrayList<>();
            plugins.add(new VanillaPlugin());
            plugins.add(new JeiInternalPlugin());
            plugins.addAll(BrbeJeiPluginFinder.findPlugins());

            StartData startData = new StartData(plugins, new HeadlessConnectionToServer());
            jeiStarter = new JeiStarter(startData);
            jeiStarter.start();
            running = true;
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] embedded JEI core started ({} plugins)", plugins.size());
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] embedded JEI core failed to start: {}", e.toString());
        }
    }

    /** Public entry: (re-)inject the server-synced mod recipes into the headless
     *  JEI manager.  Safe to call repeatedly — idempotent per type per join.
     *
     *  <p>Data source: {@code Internal#getClientSyncedRecipes()} (1.21.1 returns
     *  {@code List<RecipeHolder<?>>}).  Kept for parity with 1.21.11; BRBE's
     *  query engine reads its own index, so this is only needed when the
     *  embedded JEI runtime should also see datapack recipes. */
    public static void injectSyncedModRecipes() {
        if (!running || BrbeJeiPlatform.realJeiLoaded()) {
            return;
        }
        mezz.jei.api.recipe.IRecipeManager manager = JeiRuntimeBridge.recipeManager();
        if (manager == null) {
            return;
        }
        List<RecipeHolder<?>> holders = Internal.getClientSyncedRecipes();
        if (holders == null || holders.isEmpty()) {
            return;
        }
        // Map every JEI recipe type uid to the registered JEI recipe type.
        Map<ResourceLocation, RecipeType<?>> typesByUid = new HashMap<>();
        manager.createRecipeCategoryLookup().get()
                .forEach(category -> {
                    RecipeType<?> type = category.getRecipeType();
                    if (type != null && type.getUid() != null) {
                        typesByUid.put(type.getUid(), type);
                    }
                });
        // Group the server-synced recipe holders by their vanilla recipe type,
        // skipping vanilla types (already registered by VanillaPlugin).
        Map<ResourceLocation, List<RecipeHolder<?>>> holdersByType = new HashMap<>();
        for (RecipeHolder<?> holder : holders) {
            if (holder == null || holder.value() == null) continue;
            ResourceLocation typeKey = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            if (typeKey == null || typeKey.getNamespace().equals("minecraft")) continue;
            holdersByType.computeIfAbsent(typeKey, k -> new ArrayList<>()).add(holder);
        }
        for (Map.Entry<ResourceLocation, List<RecipeHolder<?>>> entry : holdersByType.entrySet()) {
            ResourceLocation typeKey = entry.getKey();
            if (!injectedTypes.add(typeKey)) {
                continue;
            }
            RecipeType<?> recipeType = typesByUid.get(typeKey);
            if (recipeType == null) {
                continue;
            }
            try {
                Class<?> recipeClass = recipeType.getRecipeClass();
                if (RecipeHolder.class.isAssignableFrom(recipeClass)) {
                    manager.addRecipes((RecipeType) recipeType, (List) entry.getValue());
                } else {
                    List<Object> recipes = new ArrayList<>();
                    for (RecipeHolder<?> holder : entry.getValue()) {
                        if (holder.value() != null && recipeClass.isInstance(holder.value())) {
                            recipes.add(holder.value());
                        }
                    }
                    if (!recipes.isEmpty()) {
                        manager.addRecipes((RecipeType) recipeType, recipes);
                    }
                }
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] injected {} recipes into JEI manager for {}",
                        entry.getValue().size(), typeKey);
            } catch (Exception | LinkageError e) {
                BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] failed to inject recipes for {}: {}",
                        typeKey, e.toString());
            }
        }
    }

    /** Public entry: stop the embedded core (disconnect). */
    public static void stop() {
        if (!running) {
            return;
        }
        try {
            jeiStarter.stop();
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.debug("[BRBE-JEI-Plugins] embedded JEI core stop: {}", e.toString());
        }
        running = false;
        injectedTypes.clear();
    }

    /** Client shutdown: stop the core and shut JEI's delayed executor down
     *  (mirrors real JEI's onClientStopping; otherwise the client shutdown
     *  watchdog force-crashes after the non-daemon executor thread lingers). */
    public static void onClientStopping() {
        stop();
        try {
            Internal.onClientStopping();
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.debug("[BRBE-JEI-Plugins] onClientStopping: {}", e.toString());
        }
    }

    public static boolean isRunning() {
        return running;
    }
}
