package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.config.KeybindingCodec;
import com.alonie.brbe.config.KeybindingGuiRegistrar;
import com.alonie.brbe.config.PinyinSearchGuiRegistrar;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import com.alonie.brbe.util.ConfigScreenSideText;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
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
        KeyMappingHelper.registerKeyMapping(BetterRecipeBook.PIN_MAPPING);
        KeyMappingHelper.registerKeyMapping(BetterRecipeBook.RECIPE_VIEW_MAPPING);
        KeyMappingHelper.registerKeyMapping(BetterRecipeBook.USAGE_VIEW_MAPPING);

        // 锻造 fallback：尽早注册同步配方监听（须先于登录的
        // ClientRecipeSynchronizedEvent，否则错过回调、兜底永远无数据）。
        com.alonie.brbe.cache.BrbeJeiBridge.initClient();

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
            // 会话级进度状态清空：同一会话跨存档不得继承上一个存档的解锁
            // （解锁权威 = 原版 advancement，按世界存储）。
            com.alonie.brbe.brewingstand.RecipeUnlockTracker.resetSession();
        });

        // Register optional compat handlers
        com.alonie.brbe.fabric.compat.rei.ReiCompatHandler.register();
        ModMenuReflectiveBridge.register();
        KeybindingGuiRegistrar.register();
        PinyinSearchGuiRegistrar.register();
        
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            this.registeredScreens.remove(screen);
            // 配置界面左侧的竖排装饰文字（屏幕级覆盖绘制）。
            // ⚠️ 必须**每次 init 都重新注册**、不能按屏幕去重：Fabric 在 Screen.init 的 HEAD
            // 会重建该屏幕的全部事件对象（ScreenMixin.beforeInit → createAfterExtractEvent[]），
            // 上一次注册的监听器随旧对象一起作废 —— 去重会导致「窗口缩放 / 切类别后装饰消失」。
            // 每次 init 的事件对象都是新的，所以重复注册不会叠加。
            if (ConfigScreenSideText.shouldRender(screen)) {
                // 每次 init（打开 / 切类别 / 缩放）重掷左右偏移与旋转角
                ConfigScreenSideText.onScreenInit(screen);
                ScreenEvents.afterExtract(screen).register(ConfigScreenSideText::render);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 无头 JEI registry 导入查询引擎：headless 收集在
            // AFTER_CLIENT_LEVEL_CHANGE 完成（晚于 JOIN），tick 时若 registry
            // 已有数据则导入一次（导入后置 importedOnce 旗标，不再重复）。
            // anvil/brewing/grindstone 等 vanilla JEI 类别依赖此通道。
            if (client.level != null) {
                // refresh 内部指纹排重：headless 收集分两阶段
                // （先 vanilla 后 mod，同步事件触发），registry 变化时才会
                // 重新导入查询引擎（registerType 幂等）。
                com.alonie.brbe.cache.BrbeJeiBridge.refresh();
                // 酿造/锻造进度：物品栏观察（酿造材料解锁）+ 原版进度观察
                // （锻造模板解锁 → 本地配方书注入）。
                com.alonie.brbe.brewingstand.RecipeUnlockTracker.tick(client);
            }
            // Coalesce recipe-book rebuilds: a pickup that unlocks several
            // recipes fires rebuildCollections per recipe; flush the engine
            // rebuild once per tick with the final known set.
            RecipeViewerIndex.flushEngineRebuildIfDirty();
            // 锻造 trim 的 fallback layout 轮询（同步配方/引擎条目时序未对齐时
            // 首轮 attach 失败；数据就绪后补挂一次，完成即 O(1) 返回）。
            com.alonie.brbe.cache.BrbeJeiBridge.pollSmithingLayoutFallback();
            Screen screen = client.gui.screen();
            if (screen == null || this.registeredScreens.contains(screen) || !TopLayerOverlayRenderer.hasOverlay(screen)) {
                return;
            }

            this.registeredScreens.add(screen);
            ScreenEvents.afterExtract(screen).register(TopLayerOverlayRenderer::render);
        });

        // Register built-in resource pack (Unique Dark filter textures)
        ResourceLoader.registerBuiltinPack(
                Identifier.fromNamespaceAndPath("brbe", "brbe_unique_dark"),
                FabricLoader.getInstance().getModContainer("brbe").orElseThrow(),
                Component.literal("Unique Dark - Lite ").append(Component.literal("✕").withStyle(ChatFormatting.YELLOW)).append(Component.literal(" BRBE")),
                PackActivationType.NORMAL);
    }
}
