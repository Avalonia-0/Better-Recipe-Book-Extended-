package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.neoforge.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.impl.hud.EmiHudHider;
import com.alonie.brbe.impl.hud.JeiHudHider;
import com.alonie.brbe.impl.hud.ReiHudHider;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.compat.emi.EmiCompat;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.jei.plugins.BrbeJeiHeadlessCore;
import com.alonie.brbe.jei.plugins.BrbeJeiPlugins;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.neoforge.NeoForgePlatform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * NeoForge client initializer using native NeoForge events.
 * No Architectury API dependency.
 */
public class BetterRecipeBookClientNeoForge {

    private static final Set<Screen> registeredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    public static void init(IEventBus modEventBus) {

        // Register key mappings (F = pin recipe, K = diagnostic dump)
        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(BetterRecipeBook.PIN_MAPPING);
            event.register(BetterRecipeBook.DIAGNOSTIC_MAPPING);
        });
        // Register built-in resource pack (Unique Dark filter textures)
        modEventBus.addListener(AddPackFindersEvent.class, event -> {
            event.addPackFinders(
                    ResourceLocation.fromNamespaceAndPath("zzzbrbe", "resourcepacks/zzzbrbe_unique_dark"),
                    PackType.CLIENT_RESOURCES,
                    Component.literal("Unique Dark - Lite ").append(Component.literal("✕").withStyle(ChatFormatting.YELLOW)).append(Component.literal(" BRBE")),
                    PackSource.BUILT_IN,
                    false,
                    Pack.Position.TOP);
        });
        // Register platform provider
        PlatformPotionUtilImpl.init();

        // Register PotionLoader lifecycle hooks (was in Architectury ClientLifecycleEvent.CLIENT_LEVEL_LOAD)
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel().isClientSide() && event.getLevel() instanceof ClientLevel clientLevel) {
                PotionLoader.load(clientLevel);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                PotionLoader.clear();
                BrbeJeiHeadlessCore.stop();
            }
        });

        // JEI GUI 图集（assets/jei 内嵌）：注册为客户端资源重载监听器，
        // 让弹窗渲染完整 JEI 界面（等价官方 RegisterClientReloadListenersEvent 接线）。
        modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent.class, event -> {
            try {
                mezz.jei.common.gui.textures.JeiGuiSpriteManager spriteManager =
                        mezz.jei.common.Internal.getTextures().getGuiSpriteManager();
                event.registerReloadListener(spriteManager);
            } catch (Exception | LinkageError e) {
                BetterRecipeBook.LOGGER.debug("[BRBE-JEI-Plugins] JEI gui sprite manager skipped: {}", e.toString());
            }
        });

        // 无头 JEI：真实 JEI 缺席时启动内嵌核心 + 收集 mod 插件数据。
        // 21.1.21 无 ClientLifecycleEvent——用 RecipesUpdatedEvent（配方同步，
        // 官方 JEI 1.21.1 同源）+ LevelEvent.Load 兜底；GameShuttingDownEvent 收尾。
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RecipesUpdatedEvent.class, event -> {
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes =
                    java.util.List.copyOf(event.getRecipeManager().getRecipes());
            if (!recipes.isEmpty()) {
                mezz.jei.common.Internal.setClientSyncedRecipes(recipes);
            }
            BrbeJeiHeadlessCore.start();
            BrbeJeiPlugins.collectAndInject();
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel().isClientSide() && event.getLevel() instanceof ClientLevel) {
                BrbeJeiHeadlessCore.start();
                BrbeJeiPlugins.collectAndInject();
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.GameShuttingDownEvent.class,
                event -> BrbeJeiHeadlessCore.onClientStopping());

        // Register HUD hiders (JEI + REI overlay control)
        OverlayHider.register(new JeiHudHider());
        OverlayHider.register(new ReiHudHider());
        OverlayHider.register(new EmiHudHider());

        // Initialize RBIP platform (NeoForge)
        RecipeBookIsPain.PLATFORM = new NeoForgePlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
        RecipeBookIsPain.LOGGER.info("[RBIP] NeoForge platform initialized");

        // Defer REI compat + RBIP init until first screen load
        ReiCompat.register();
        EmiCompat.register();
        RecipeBookIsPain.ensureInitialized();
        RecipeBookIsPain.LOGGER.info(RecipeBookIsPain.diagnostic());

        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            Screen screen = event.getScreen();
            if (screen != null) {
                registeredScreens.remove(screen);
                OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
            }
        });

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft client = Minecraft.getInstance();
            Screen screen = client.screen;
            if (BetterRecipeBook.config.hideReiJeiOverlay && screen != null) {
                OverlayHider.ensureJeiOverlayHidden();
            }
            if (screen == null || registeredScreens.contains(screen) || !TopLayerOverlayRenderer.hasOverlay(screen)) {
                return;
            }

            registeredScreens.add(screen);
            NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, renderEvent -> {
                if (renderEvent.getScreen() == screen) {
                    TopLayerOverlayRenderer.render(screen, renderEvent.getGuiGraphics(), renderEvent.getMouseX(), renderEvent.getMouseY(), renderEvent.getPartialTick());
                }
            });
        });
    }
}
