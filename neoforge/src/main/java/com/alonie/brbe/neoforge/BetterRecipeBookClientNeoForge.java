package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.brewingstand.neoforge.PlatformPotionUtilImpl;
import com.alonie.brbe.config.KeybindingGuiRegistrar;
import com.alonie.brbe.config.RecipeViewerGuiRegistrar;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.compat.emi.EmiCompat;
import com.alonie.brbe.compat.rei.ReiCompat;
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

    /** 拼音搜索的语言默认值只在启动后收敛一次（见 init 的 ClientTickEvent.Post）。 */
    private static boolean pinyinDefaultsApplied;

    public static void init(IEventBus modEventBus) {

        // 日志恒写 <gameDir>/logs/brbe-debug.log（没有开关）：BRBE、无头 JEI、
        // 以及被路由过来的官方 mezz.jei 行都在同一个文件里，latest.log 保持干净。
        // 幂等：common 的 BetterRecipeBook.init() 已调用过时内部直接返回（writer != null）。
        com.alonie.brbe.util.BrbeLogger.init(Minecraft.getInstance().gameDirectory.toPath());

        // Register key mappings (A = pin recipe, R = view recipe, U = view usage,
        // F8 = diagnostic dump).  R/U 此前在 neoforge 端漏注册（fabric 对称注册）——
        // 未注册的 KeyMapping 不进入 options.keyMappings，控制界面不可见且无法重绑。
        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(BetterRecipeBook.PIN_MAPPING);
            event.register(BetterRecipeBook.RECIPE_VIEW_MAPPING);
            event.register(BetterRecipeBook.USAGE_VIEW_MAPPING);
            event.register(BetterRecipeBook.CYCLE_LOCK_MAPPING);
            event.register(BetterRecipeBook.DIAGNOSTIC_MAPPING);
        });
        // Register built-in resource pack (Unique Dark filter textures)
        modEventBus.addListener(AddPackFindersEvent.class, event -> {
            event.addPackFinders(
                    ResourceLocation.fromNamespaceAndPath("brbe", "resourcepacks/brbe_unique_dark"),
                    PackType.CLIENT_RESOURCES,
                    Component.literal("Unique Dark - Lite ").append(Component.literal("✕").withStyle(ChatFormatting.YELLOW)).append(Component.literal(" BRBE")),
                    PackSource.BUILT_IN,
                    false,
                    Pack.Position.TOP);
        });
        // Register platform provider
        PlatformPotionUtilImpl.init();

        // /brbe 客户端指令（clear 子命令）。RegisterClientCommandsEvent 是游戏总线事件；
        // 指令树与加载器无关，这里只提供源类型适配（CommandSourceStack）。
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, event ->
                        event.getDispatcher().register(com.alonie.brbe.command.BrbeCommandTree.build(
                                new com.alonie.brbe.command.BrbeCommandTree.Feedback<net.minecraft.commands.CommandSourceStack>() {
                                    @Override
                                    public void success(net.minecraft.commands.CommandSourceStack source, String langKey, Object... args) {
                                        source.sendSuccess(() -> Component.translatable(langKey, args), false);
                                    }

                                    @Override
                                    public void failure(net.minecraft.commands.CommandSourceStack source, String langKey, Object... args) {
                                        source.sendFailure(Component.translatable(langKey, args));
                                    }
                                })));

        // Register PotionLoader lifecycle hooks (was in Architectury ClientLifecycleEvent.CLIENT_LEVEL_LOAD)
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel().isClientSide() && event.getLevel() instanceof ClientLevel clientLevel) {
                PotionLoader.load(clientLevel);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                PotionLoader.clear();
            }
        });

        // 无头 JEI 桥：headless-jei 独立 mod 负责采集与运行时；BRBE 这里只把
        // 其 JeiRecipeRegistry 条目索引进查询引擎（absent 时静默跳过）。
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel().isClientSide() && event.getLevel() instanceof ClientLevel) {
                // level 就绪：重置启动闸（允许真正的 JEI start——atlas 此时已载入）
                // + 置位收集；refresh 消费。
                com.alonie.brbe.cache.BrbeJeiBridge.retryStart();
                com.alonie.brbe.cache.BrbeJeiBridge.refresh();
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RecipesUpdatedEvent.class,
                event -> {
                    // 配方同步了（mod 配方晚于 vanilla）：重置启动闸再收集导入。
                    com.alonie.brbe.cache.BrbeJeiBridge.retryStart();
                    com.alonie.brbe.cache.BrbeJeiBridge.refresh();
                });

        // JEI GUI 图集重载监听：headless-jei 核心 start() 依赖 atlas 初始化，否则抛
        // "atlas is not initialized" 且 running 不置位 → BRBE 每 tick 重试完整启动
        // （1854 次/百秒卡顿）。原 neoforge 入口的 @Mod 从不调用，这里 BRBE 反射补注册。
        modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent.class,
                com.alonie.brbe.cache.BrbeJeiBridge::registerAtlasReloadListener);

        // Cloth Config 键位配置项（R/U/A 键）：与 fabric 端对称注册，
        // 否则配置界面显示为原始文本框（raw 键名未翻译）。
        KeybindingGuiRegistrar.register();
        RecipeViewerGuiRegistrar.register();
        // 拼音搜索配置项的条件显示（中文语言外隐藏该选项）——本端此前漏注册，
        // 导致配置界面在所有语言下都显示该项（fabric 端一直有）。
        com.alonie.brbe.config.PinyinSearchGuiRegistrar.register();

        // Initialize RBIP platform (NeoForge)
        RecipeBookIsPain.PLATFORM = new NeoForgePlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
        com.alonie.brbe.util.BrbeLogger.log("RBIP", "NeoForge platform initialized");

        // Defer REI compat + RBIP init until first screen load
        ReiCompat.register();
        EmiCompat.register();
        RecipeBookIsPain.ensureInitialized();
        com.alonie.brbe.util.BrbeLogger.log("RBIP", "{}", RecipeBookIsPain.diagnostic());

        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            Screen screen = event.getScreen();
            if (screen != null) {
                registeredScreens.remove(screen);
                // 每次 init（打开 / 切类别 / 缩放）重掷两侧竖排文字的之字形横向偏移
                com.alonie.brbe.util.ConfigScreenSideText.onScreenInit(screen);
                // 查询浮层：整屏渲染完成后绘制（最顶层）——Screen.render TAIL 在容器
                // 内容之前执行，浮层会被背包/配方书盖住（R 打开但面板被遮挡 = "无法使用"）。
                NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, renderEvent -> {
                    if (renderEvent.getScreen() == screen) {
                        TopLayerOverlayRenderer.renderViewer(screen, renderEvent.getGuiGraphics(),
                                renderEvent.getMouseX(), renderEvent.getMouseY(), renderEvent.getPartialTick());
                    }
                });
            }
        });

        // 配置界面两侧的竖排装饰文字（屏幕级覆盖绘制）：全局 Render.Post 监听 +
        // 现场过滤屏幕类型，无需像 fabric 那样按屏幕注册（也就没有重复注册问题）。
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, renderEvent -> {
            com.alonie.brbe.util.ConfigScreenSideText.render(
                    renderEvent.getScreen(), renderEvent.getGuiGraphics(),
                    renderEvent.getMouseX(), renderEvent.getMouseY(), renderEvent.getPartialTick());
        });

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft client = Minecraft.getInstance();
            Screen screen = client.screen;
            // 查询引擎：dirty 合并 flush（配方书重建/解锁变化在 tick 末落盘一次）
            com.alonie.brbe.cache.RecipeViewerIndex.flushEngineRebuildIfDirty();
            // 无头 JEI 桥：headless-jei 采集与配方同步是分阶段/异步的，LevelEvent.Load/
            // RecipesUpdated 一次性 refresh 会读到空 registry（mod 类别/anvil/brewing/
            // grindstone 缺失）。每 tick 轮询（指纹去重，见 BrbeJeiBridge.refresh），
            // 1.21.11 同策略。
            if (client.level != null) {
                com.alonie.brbe.cache.BrbeJeiBridge.refresh();
            }
            // 拼音搜索：启动后一次性收敛到语言默认值（中文 = 开 / 其他语言 = 关）。
            // 本端此前完全没有该逻辑（fabric 端在 CLIENT_STARTED 里做）——NeoForge 无
            // 与 CLIENT_STARTED 直接对应的事件，首个客户端 tick 是等价的
            // "初始化完成、只跑一次"时机；判定与 /brbe clear configchange 共用
            // PinyinSearchDefaults（见 BrbeCommandActions.resetConfig）。
            if (!pinyinDefaultsApplied) {
                pinyinDefaultsApplied = true;
                if (BetterRecipeBook.config != null && BetterRecipeBook.configHolder != null
                        && com.alonie.brbe.config.PinyinSearchDefaults.applyLanguageDefault(
                                BetterRecipeBook.config, client)) {
                    BetterRecipeBook.configHolder.save();
                }
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
