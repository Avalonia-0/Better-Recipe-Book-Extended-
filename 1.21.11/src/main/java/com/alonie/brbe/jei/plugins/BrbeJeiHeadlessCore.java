package com.alonie.brbe.jei.plugins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.common.Internal;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PlayToServerPacket;
import mezz.jei.fabric.network.ConnectionToServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import mezz.jei.fabric.startup.FabricPluginFinder;
import mezz.jei.library.plugins.jei.JeiInternalPlugin;
import mezz.jei.library.plugins.vanilla.VanillaPlugin;
import mezz.jei.library.startup.JeiStarter;
import mezz.jei.library.startup.StartData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Initializes the embedded full JEI runtime when the real JEI mod is NOT
 * installed, so BRBE's preview/pin popups render the real JEI front-end
 * (category background, slot backgrounds, drawables, animations) via
 * {@code IRecipeManager#createRecipeLayoutDrawable} instead of the vanilla
 * fallback.
 *
 * <p>No JEI GUI is registered here: the Gui module's classes are bundled but
 * never instantiated, and this initializer never touches {@code ScreenEvents}
 * or keybinding registration — so with JEI absent the player sees no JEI
 * feature at all.  The runtime built here only feeds BRBE's own preview/pin
 * popups through {@code JeiRuntimeBridge}.
 *
 * <p>When the real JEI is installed it registers before BRBE (mod id
 * {@code jei} &lt; {@code zzzbrbe}) and its classes win on the classpath, so
 * the identical {@code mezz.jei.*} classes bundled here are shadowed and this
 * initializer is skipped.
 */
public final class BrbeJeiHeadlessCore {

    private static final Logger LOGGER = LogManager.getLogger("headless-jei");

    private BrbeJeiHeadlessCore() {}

    private static JeiStarter jeiStarter;
    private static boolean running;

    /** Recipe types already injected into the headless JEI recipe manager this
     *  server join (dedupe guard: addRecipes appends, so re-injecting a type
     *  would duplicate its recipes).  Cleared by {@link #stop()}. */
    private static final Set<Identifier> injectedTypes = new HashSet<>();

    /** Register recipes sync + lifecycle hooks.  Called once from the client
     *  entrypoint. */
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("jei")) {
            return;
        }

        // The embedded core needs the server-synced recipe registry, exactly
        // like real JEI's JustEnoughItemsClient does on Fabric.
        ClientRecipeSynchronizedEvent.EVENT.register((minecraft, synchronizedRecipes) -> {
            Internal.setClientSyncedRecipes(RecipeMap.create(synchronizedRecipes.recipes()));
            start();
            // The sync event carries the FULL synchronized set (the JOIN
            // fallback may have started the core earlier with only vanilla
            // fallback recipes); re-inject now that the mod recipes are here.
            // injectSyncedModRecipes is idempotent per type (injectedTypes).
            injectSyncedModRecipes();
        });

        // Fallback kick: the sync event may fire before the client level is
        // bound (start() then defers); a level load always follows, so retry
        // there.  start() is idempotent (running guard + JeiStarter guard).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> start());

        // The JEI GUI atlas is registered before the initial resource reload
        // by BrbeJeiMinecraftMixin (equivalent to JEI's own MinecraftMixin),
        // so it gets stitched with the rest of the GUI textures.

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());

        // The embedded core's DelayedExecutor owns a NON-daemon thread ("JEI
        // Delayed Executor 0"); without a shutdown it keeps the JVM alive after
        // the game exits, and the client's shutdown watchdog force-crashes
        // ("Client shutdown from post-main") after 60s.  The real JEI shuts it
        // down from CLIENT_STOPPING (via Internal.onClientStopping) — mirror
        // that here.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            stop();
            mezz.jei.common.Internal.onClientStopping();
        });
    }

    /** Build and start the embedded JEI runtime, mirroring JEI's
     *  ClientLifecycleHandler#startJei but without any GUI registration. */
    private static void start() {
        if (running) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            LOGGER.info("[BRBE-JEI-Plugins] no level yet; deferring embedded core start");
            return;
        }
        try {
            // The real JEI sends a chat warning when the server lacks JEI
            // (verifyClientRecipes).  In headless mode the embedded core IS the
            // server's JEI (recipes come from the same client sync), so report
            // jei-on-server=true to keep that warning silent.
            //
            // Additionally, ensure hasClientRecipes() is true BEFORE JeiStarter
            // runs verifyClientRecipes: otherwise it sets a fallback recipe map
            // and (with isJeiOnServer()=true bypassing the jei.missing branch)
            // falls through to the "server does not provide recipes" warning.
            // The real JEI client gets these from the fabric recipe-sync event;
            // re-apply the bundled vanilla recipes here as a headless fallback.
            if (!Internal.hasClientRecipes()) {
                net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;
                if (level != null) {
                    RecipeMap vanillaRecipes = mezz.jei.common.recipes.VanillaClientRecipeLoader
                            .getVanillaRecipes(level.registryAccess());
                    if (!vanillaRecipes.values().isEmpty()) {
                        Internal.setClientSyncedRecipes(vanillaRecipes);
                    }
                }
            }

            IConnectionToServer serverConnection = new HeadlessConnectionToServer();
            Internal.setServerConnection(serverConnection);
            Internal.setKeyMappings(new com.alonie.brbe.jei.plugins.engine.HeadlessKeyMappings());

            List<IModPlugin> plugins = new ArrayList<>();
            // JEI's own plugins are declared in JEI's fabric.mod.json; without
            // the real JEI installed they are absent from the entrypoint scan,
            // so the embedded Library copies are added by hand.  VanillaPlugin
            // is mandatory for JeiStarter.
            plugins.add(new VanillaPlugin());
            plugins.add(new JeiInternalPlugin());
            plugins.addAll(FabricPluginFinder.getModPlugins());

            StartData startData = new StartData(plugins, serverConnection);
            jeiStarter = new JeiStarter(startData);
            jeiStarter.start();
            running = true;
            LOGGER.info("[BRBE-JEI-Plugins] embedded JEI core started ({} plugins)", plugins.size());
            injectSyncedModRecipes();
        } catch (Exception | LinkageError e) {
            LOGGER.warn("[BRBE-JEI-Plugins] embedded JEI core failed to start: {}", e.toString());
        }
    }

    /** Public entry: (re-)inject the server-synced mod recipes into the headless
     *  JEI manager.  Safe to call repeatedly — idempotent per type per join.
     *  Called from {@code BrbeJeiPlugins.collectAndInject} so the manager is
     *  complete by the time the query engine reads it, even if the recipes
     *  arrived after the embedded core started.
     *
     *  <p>Data source: {@code Internal#getClientSyncedRecipes()}, the same
     *  source the {@code RecipeCollector} fallback uses on this branch
     *  (fabric-recipe-api 8.x has no FabricRecipeAccess). */
    public static void injectSyncedModRecipes() {
        // With the real JEI installed, mods register their recipes through it
        // normally — injection is only for the headless embedded core.
        if (FabricLoader.getInstance().isModLoaded("jei")) {
            return;
        }
        IRecipeManager manager = JeiRuntimeBridge.recipeManager();
        if (manager == null) {
            return;
        }
        RecipeMap recipeMap = Internal.getClientSyncedRecipes();
        if (recipeMap == null || recipeMap.values().isEmpty()) {
            return;
        }
        // Map every JEI type uid (identifier) to the registered JEI recipe type.
        Map<Identifier, IRecipeType<?>> typesByUid = new HashMap<>();
        manager.createRecipeCategoryLookup().get()
                .forEach(category -> {
                    IRecipeType<?> type = category.getRecipeType();
                    if (type.getUid() != null) {
                        typesByUid.put(type.getUid(), type);
                    }
                });
        // Group the server-synced recipe holders by their vanilla recipe type.
        Map<Identifier, List<RecipeHolder<?>>> holdersByType = new HashMap<>();
        for (RecipeHolder<?> holder : recipeMap.values()) {
            if (holder == null || holder.value() == null) continue;
            Identifier typeKey = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            if (typeKey == null) continue;
            if (typeKey.getNamespace().equals("minecraft")) continue;
            holdersByType.computeIfAbsent(typeKey, k -> new ArrayList<>()).add(holder);
        }
        for (Map.Entry<Identifier, List<RecipeHolder<?>>> entry : holdersByType.entrySet()) {
            Identifier typeKey = entry.getKey();
            if (!injectedTypes.add(typeKey)) {
                continue;
            }
            IRecipeType<?> recipeType = typesByUid.get(typeKey);
            if (recipeType == null) {
                continue;
            }
            try {
                Class<?> recipeClass = recipeType.getRecipeClass();
                if (RecipeHolder.class.isAssignableFrom(recipeClass)) {
                    // Holder-typed JEI type (IRecipeHolderType): the manager
                    // stores RecipeHolder instances directly.
                    manager.addRecipes((IRecipeType) recipeType, (List) entry.getValue());
                } else {
                    // Bare-typed JEI type (IRecipeType<T> with T the concrete
                    // recipe class): unwrap the holders to the recipe values.
                    List<Object> recipes = new ArrayList<>();
                    for (RecipeHolder<?> holder : entry.getValue()) {
                        if (holder.value() != null && recipeClass.isInstance(holder.value())) {
                            recipes.add(holder.value());
                        }
                    }
                    if (!recipes.isEmpty()) {
                        manager.addRecipes((IRecipeType) recipeType, recipes);
                    }
                }
                LOGGER.info("[BRBE-JEI-Plugins] injected {} recipes into JEI manager for {}",
                        entry.getValue().size(), typeKey);
            } catch (Exception | LinkageError e) {
                LOGGER.warn("[BRBE-JEI-Plugins] failed to inject recipes for {}: {}",
                        typeKey, e.toString());
            }
        }
    }

    private static void stop() {
        if (!running) {
            return;
        }
        try {
            jeiStarter.stop();
        } catch (Exception | LinkageError e) {
            LOGGER.debug("[BRBE-JEI-Plugins] embedded JEI core stop: {}", e.toString());
        }
        running = false;
        injectedTypes.clear();
    }

    public static boolean isRunning() {
        return running;
    }

    /** Wraps {@link ConnectionToServer} but reports "JEI is on the server" so
     *  the embedded core's verifyClientRecipes does not spam the missing-JEI
     *  chat warning.  Packet plumbing still goes through the real fabric
     *  connection. */
    private static final class HeadlessConnectionToServer implements IConnectionToServer {
        private final ConnectionToServer delegate = new ConnectionToServer();

        @Override
        public boolean isJeiOnServer() {
            return true;
        }

        @Override
        public boolean isSameModLoader() {
            return delegate.isSameModLoader();
        }

        @Override
        public boolean canSendPacket(CustomPacketPayload.Type<?> packetType) {
            return delegate.canSendPacket(packetType);
        }

        @Override
        public <T extends PlayToServerPacket<T>> void sendPacketToServer(T packet) {
            delegate.sendPacketToServer(packet);
        }
    }
}
