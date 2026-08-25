package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.config.KeybindingCodec;
import com.alonie.brbe.config.KeybindingGuiRegistrar;
import com.alonie.brbe.config.PinyinSearchGuiRegistrar;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.alonie.brbe.impl.hud.JeiHudHider;
import com.alonie.brbe.impl.hud.ReiHudHider;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class BetterRecipeBookClientFabric implements ClientModInitializer {
    private final Set<Screen> registeredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    /** 启动时以配置（brbe.toml）为权威，把固定/查询键同步到原版 KeyMapping 并
     *  持久化到 options.txt（防 Options.load 旧值覆盖导致的键位回退）。 */
    private static void syncConfigKeyMappings(Minecraft client) {
        if (BetterRecipeBook.config == null) return;
        try {
            applyConfigKey(BetterRecipeBook.PIN_MAPPING, BetterRecipeBook.config.pinKey);
            applyConfigKey(BetterRecipeBook.RECIPE_VIEW_MAPPING, BetterRecipeBook.config.recipeViewKey);
            applyConfigKey(BetterRecipeBook.USAGE_VIEW_MAPPING, BetterRecipeBook.config.usageViewKey);
            client.options.save();
        } catch (Throwable ignored) {
            // 同步失败不影响启动
        }
    }

    private static void applyConfigKey(KeyMapping mapping, String raw) {
        if (mapping == null) return;
        ModifierKeyCode mkc = KeybindingCodec.decode(raw);
        if (mkc == null || mkc.isUnknown()) return;
        // 无条件 setKey：幂等（KeyMappingSyncMixin 会把同值写回配置），
        // KeyMapping 没有公开的当前键 getter 可做相等性短路。
        mapping.setKey(mkc.getKeyCode());
    }

    @Override
    public void onInitializeClient() {
        // Register key mappings (previously in common via Architectury).
        // 固定键与查询键的原版绑定与 Cloth Config 键位条目双向同步
        // （KeyMapping.setKey 写回配置，Cloth 保存时写回 KeyMapping）。
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.PIN_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.RECIPE_VIEW_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.USAGE_VIEW_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.DIAGNOSTIC_MAPPING);

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
            // 配置键为权威：启动时同步回原版 KeyMapping 并持久化到 options.txt。
            // Options.load 可能用 options.txt 的旧值（如早期版本保存的 F）覆盖
            // KeyMapping 并经由 KeyMappingSyncMixin 写回配置——这里以 brbe.toml
            // 为准收敛，保证固定/查询键重启后不回退。
            syncConfigKeyMappings(client);
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

        // Register optional compat handlers
        com.alonie.brbe.fabric.compat.rei.ReiCompatHandler.register();
        KeybindingGuiRegistrar.register();
        PinyinSearchGuiRegistrar.register();

        // Register HUD hiders (JEI + REI overlay control)
        OverlayHider.register(new JeiHudHider());
        OverlayHider.register(new ReiHudHider());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            this.registeredScreens.remove(screen);
            // Apply overlay hide state immediately when screen opens (no flash)
            OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Coalesce recipe-book rebuilds: a pickup that unlocks several
            // recipes fires rebuildCollections per recipe; flush the engine
            // rebuild once per tick with the final known set.
            RecipeViewerIndex.flushEngineRebuildIfDirty();
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
                Identifier.fromNamespaceAndPath("zzzbrbe", "zzzbrbe_unique_dark"),
                FabricLoader.getInstance().getModContainer("zzzbrbe").orElseThrow(),
                Component.literal("Unique Dark Lite ").append(Component.literal("✕").withStyle(ChatFormatting.YELLOW)).append(Component.literal(" BRBE")),
                ResourcePackActivationType.NORMAL);
    }
}
