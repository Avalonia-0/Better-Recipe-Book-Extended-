package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.impl.hud.EmiHudHider;
import com.alonie.brbe.impl.hud.JeiHudHider;
import com.alonie.brbe.impl.hud.ReiHudHider;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.compat.emi.EmiCompat;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import com.alonie.brbe.config.KeybindingGuiRegistrar;
import com.alonie.brbe.config.PinyinSearchGuiRegistrar;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.fabric.FabricPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class BetterRecipeBookClientFabric implements ClientModInitializer {
    private final Set<Screen> registeredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onInitializeClient() {
        // Register key mappings (previously in common via Architectury KeyMappingRegistry)
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.PIN_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.DIAGNOSTIC_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.RECIPE_VIEW_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.USAGE_VIEW_MAPPING);

        // 拼音搜索：中文语言（zh_*）默认开启（用户仍可手动关闭）；
        // 非中文语言强制关闭（配置界面同时隐藏该选项，见 PinyinSearchGuiRegistrar）。
        // 注：entrypoint 阶段 Minecraft.options 尚为 null，须延迟到 CLIENT_STARTED
        // （客户端初始化完成、仅触发一次）。
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (BetterRecipeBook.config == null || BetterRecipeBook.configHolder == null) return;
            String languageCode = client.options.languageCode;
            boolean chinese = languageCode != null && languageCode.startsWith("zh");
            if (chinese && !BetterRecipeBook.config.pinyinSearch) {
                BetterRecipeBook.config.pinyinSearch = true;
                BetterRecipeBook.configHolder.save();
            } else if (!chinese && BetterRecipeBook.config.pinyinSearch) {
                BetterRecipeBook.config.pinyinSearch = false;
                BetterRecipeBook.configHolder.save();
            }
        });

        // Register platform-specific providers
        PlatformPotionUtilImpl.init();

        // Register PotionLoader lifecycle hooks (was in Architectury ClientLifecycleEvent.CLIENT_LEVEL_LOAD)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.level != null) PotionLoader.load(client.level);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PotionLoader.clear();
        });

        // Register HUD hiders (JEI + REI overlay control)
        OverlayHider.register(new JeiHudHider());
        OverlayHider.register(new ReiHudHider());
        OverlayHider.register(new EmiHudHider());

        // Initialize RBIP platform (Fabric)
        RecipeBookIsPain.PLATFORM = new FabricPlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
        RecipeBookIsPain.LOGGER.info("[RBIP] Fabric platform initialized");
        RecipeBookIsPain.LOGGER.info(RecipeBookIsPain.diagnostic());
        RecipeBookIsPain.ensureInitialized();
        RecipeBookIsPain.LOGGER.info(RecipeBookIsPain.diagnostic());

        // Register optional compat handlers
        ReiCompat.register();
        EmiCompat.register();
        KeybindingGuiRegistrar.register();
        PinyinSearchGuiRegistrar.register();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            this.registeredScreens.remove(screen);
            OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen screen = client.screen;
            if (BetterRecipeBook.config.hideReiJeiOverlay && screen != null) {
                OverlayHider.ensureJeiOverlayHidden();
            }
            if (screen == null || this.registeredScreens.contains(screen) || !TopLayerOverlayRenderer.hasOverlay(screen)) {
                return;
            }

            this.registeredScreens.add(screen);
            ScreenEvents.afterRender(screen).register(TopLayerOverlayRenderer::render);
        });

        // Register built-in resource pack (Unique Dark filter textures)
        ResourceManagerHelper.registerBuiltinResourcePack(
                ResourceLocation.fromNamespaceAndPath("zzzbrbe", "zzzbrbe_unique_dark"),
                FabricLoader.getInstance().getModContainer("zzzbrbe").orElseThrow(),
                Component.literal("Unique Dark - Lite ").append(Component.literal("✕").withStyle(ChatFormatting.YELLOW)).append(Component.literal(" BRBE")),
                ResourcePackActivationType.NORMAL);
    }
}
