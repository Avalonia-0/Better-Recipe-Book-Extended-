package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.fabric.PlatformPotionUtilImpl;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.compat.emi.EmiCompat;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.util.TopLayerOverlayRenderer;
import com.alonie.brbe.util.ConfigScreenSideText;
import com.alonie.brbe.config.KeybindingGuiRegistrar;
import com.alonie.brbe.config.PinyinSearchGuiRegistrar;
import com.alonie.brbe.config.RecipeViewerGuiRegistrar;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.fabric.FabricPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
        // 日志恒写 <gameDir>/logs/brbe-debug.log（没有开关）：BRBE、无头 JEI、
        // 以及被路由过来的官方 mezz.jei 行都在同一个文件里，latest.log 保持干净。
        // 幂等：common 的 BetterRecipeBook.init() 已调用过时内部直接返回（writer != null）。
        com.alonie.brbe.util.BrbeLogger.init(Minecraft.getInstance().gameDirectory.toPath());

        // Register key mappings (previously in common via Architectury KeyMappingRegistry)
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.PIN_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.DIAGNOSTIC_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.RECIPE_VIEW_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.USAGE_VIEW_MAPPING);
        KeyBindingHelper.registerKeyBinding(BetterRecipeBook.CYCLE_LOCK_MAPPING);

        // /brbe 客户端指令（clear 子命令）。指令树与加载器无关，这里只提供源类型适配。
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(com.alonie.brbe.command.BrbeCommandTree.build(
                        new com.alonie.brbe.command.BrbeCommandTree.Feedback<FabricClientCommandSource>() {
                            @Override
                            public void success(FabricClientCommandSource source, String langKey, Object... args) {
                                source.sendFeedback(Component.translatable(langKey, args));
                            }

                            @Override
                            public void failure(FabricClientCommandSource source, String langKey, Object... args) {
                                source.sendError(Component.translatable(langKey, args));
                            }
                        })));

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
            // 无头 JEI 桥：headless-jei 独立 mod 的配方条目索引进查询引擎。
            // JOIN 重置启动闸（atlas 已载入）+ 置位收集；refresh 消费一次后
            // 不再每 tick 重试（见 BrbeJeiBridge.startAttempted）。
            com.alonie.brbe.cache.BrbeJeiBridge.retryStart();
            com.alonie.brbe.cache.BrbeJeiBridge.refresh();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PotionLoader.clear();
        });

        // Initialize RBIP platform (Fabric)
        RecipeBookIsPain.PLATFORM = new FabricPlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
        com.alonie.brbe.util.BrbeLogger.log("RBIP", "Fabric platform initialized");
        com.alonie.brbe.util.BrbeLogger.log("RBIP", "{}", RecipeBookIsPain.diagnostic());
        RecipeBookIsPain.ensureInitialized();
        com.alonie.brbe.util.BrbeLogger.log("RBIP", "{}", RecipeBookIsPain.diagnostic());

        // Register optional compat handlers
        ReiCompat.register();
        EmiCompat.register();
        KeybindingGuiRegistrar.register();
        PinyinSearchGuiRegistrar.register();
        RecipeViewerGuiRegistrar.register();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            this.registeredScreens.remove(screen);
            // 配置界面左侧的竖排装饰文字（屏幕级覆盖绘制）。
            // ⚠️ 必须**每次 init 都重新注册**、不能按屏幕去重：Fabric 在 Screen.init 的 HEAD
            // 会重建该屏幕的全部事件对象（ScreenMixin.beforeInit → createAfterExtractEvent[]），
            // 上一次注册的监听器随旧对象一起作废 —— 去重会导致「窗口缩放 / 切类别后装饰消失」。
            // 每次 init 的事件对象都是新的，所以重复注册不会叠加。
            // 按屏幕**类型**注册（不看开关）：「隐藏配置界面两侧的文字」在 render 里每帧判定，
            // 这样在配置界面里切换开关两个方向都立即生效。
            if (ConfigScreenSideText.isDecoratedScreen(screen)) {
                // 每次 init（打开 / 切类别 / 缩放）重掷左右偏移与旋转角
                ConfigScreenSideText.onScreenInit(screen);
                ScreenEvents.afterRender(screen).register(ConfigScreenSideText::render);
            }
            // 查询浮层：整屏渲染完成后绘制（最顶层）——Screen.render TAIL 在容器
            // 内容之前执行，浮层会被背包/配方书盖住。
            ScreenEvents.afterRender(screen).register(TopLayerOverlayRenderer::renderViewer);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen screen = client.screen;
            // 查询引擎：dirty 合并 flush（配方书重建/解锁变化在 tick 末落盘一次）
            com.alonie.brbe.cache.RecipeViewerIndex.flushEngineRebuildIfDirty();
            // 无头 JEI 桥：headless-jei 采集与配方同步是分阶段/异步的，JOIN 一次性
            // refresh 会读到空 registry（mod 类别/anvil/brewing/grindstone 缺失）。
            // 每 tick 轮询（指纹去重，见 BrbeJeiBridge.refresh），1.21.11 同策略。
            if (client.level != null) {
                com.alonie.brbe.cache.BrbeJeiBridge.refresh();
            }
            if (screen == null || this.registeredScreens.contains(screen) || !TopLayerOverlayRenderer.hasOverlay(screen)) {
                return;
            }

            this.registeredScreens.add(screen);
            ScreenEvents.afterRender(screen).register(TopLayerOverlayRenderer::render);
        });

        // Register built-in resource pack (Unique Dark filter textures)
        ResourceManagerHelper.registerBuiltinResourcePack(
                ResourceLocation.fromNamespaceAndPath("brbe", "brbe_unique_dark"),
                FabricLoader.getInstance().getModContainer("brbe").orElseThrow(),
                Component.literal("Unique Dark - Lite ").append(Component.literal("✕").withStyle(ChatFormatting.YELLOW)).append(Component.literal(" BRBE")),
                ResourcePackActivationType.NORMAL);
    }
}
