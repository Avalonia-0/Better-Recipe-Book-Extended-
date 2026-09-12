package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine.JeiEntry;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine.JeiSlot;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine.RecipeDisplayId;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine.RecipeLayout;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine.RecipeSlotLayout;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.21.1 无头 JEI 桥（BRBE 侧，纯反射）：从独立项目 headless-jei mod 的
 * {@code JeiRecipeRegistry}（轻量桥）拉取 JEI 配方条目，转进 BRBE 查询引擎
 * 的 {@link RecipeViewerEngine#registerJeiType}。
 *
 * <p>headless-jei 产物按 intermediary 映射发布（与 BRBE 核心 jar 一致），
 * 其桥 API 无法直接用于 BRBE 的 mojang 编译——改用纯反射调用；
 * headless-jei mod 缺席（真实 JEI 或纯原版）时所有调用静默跳过，
 * anvil/grindstone 等类别降级为信息页。</p>
 */
public final class BrbeJeiBridge {

    private BrbeJeiBridge() {}

    private static Class<?> registryClass;
    private static Method typeIdsMethod;
    private static Method entriesForMethod;
    private static Method stationsForMethod;
    private static Method titleForMethod;
    private static Method headlessStartMethod;
    private static Method headlessIsRunningMethod;
    private static Method pluginsCollectMethod;

    /** 上次成功导入的注册表指纹（类型+条数）；每 tick 轮询去重用。 */
    private static String lastSignature;

    /** 待收集标志：start 转换 / 配方同步事件置位，refresh() 消费一次后清除。
     *  使 collectAndInject()（重）不在每 tick 轮询中反复执行（此前每 tick 全量
     *  收集 JEI 配方索引 → 662ms 卡顿根因）。 */
    private static boolean collectPending;

    /** 切石/锻造：条目由 RecipeViewerIndex（holder 通道）注册；headless 的
     *  JEI 运行时条目仅用于给已有 holder 条目附着原生布局（弹窗委托完整 JEI
     *  UI）。匹配键 = holder.id()（同一 RecipeManager 数据源，id 恒等）。 */
    private static final List<String> ATTACH_TYPES =
            List.of("minecraft:stonecutting", "minecraft:smithing");

    private static final Map<RecipeDisplayId, ResourceLocation> ATTACHED_UID_BY_ID = new HashMap<>();
    private static final Map<RecipeDisplayId, Object> ATTACHED_RECIPE_BY_ID = new HashMap<>();
    /** attach 后的完整 JeiEntry（弹窗 1:1 委托直接用；含 typeUid+recipe+槽位）。 */
    private static final Map<RecipeDisplayId, JeiEntry> ATTACHED_ENTRY_BY_ID = new HashMap<>();

    /** 引擎 holder 通道重建后可重新附着（重建晚于桥导入时附着曾空跑）。 */
    private static boolean attachDirty = true;

    /** Whether the headless-jei mod is present on the classpath. */
    public static synchronized boolean available() {
        if (registryClass != null) {
            return true;
        }
        try {
            registryClass = Class.forName("com.alonie.brbe.jei.api.JeiRecipeRegistry");
            typeIdsMethod = registryClass.getMethod("typeIds");
            entriesForMethod = registryClass.getMethod("entriesFor", ResourceLocation.class);
            stationsForMethod = registryClass.getMethod("stationsFor", ResourceLocation.class);
            titleForMethod = registryClass.getMethod("titleFor", ResourceLocation.class);
            // 启动无头 JEI 核心所需的两处静态方法（可选：缺失时跳过启动）。
            try {
                headlessStartMethod = Class.forName("com.alonie.brbe.jei.plugins.BrbeJeiHeadlessCore")
                        .getMethod("start");
                headlessIsRunningMethod = Class.forName("com.alonie.brbe.jei.plugins.BrbeJeiHeadlessCore")
                        .getMethod("isRunning");
                pluginsCollectMethod = Class.forName("com.alonie.brbe.jei.plugins.BrbeJeiPlugins")
                        .getMethod("collectAndInject");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // 无启动方法（旧桥）——调用方仍可读已填充的 registry。
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            registryClass = null;
            return false;
        }
    }

    /** 确保无头 JEI 核心已启动（只 start()，幂等；不在此收集——收集改由
     *  {@link #requestCollect()} 置位、{@link #refresh()} 消费，避免每 tick 全量
     *  收集造成卡顿）。
     *
     *  <p>headless-jei 作为 jar-in-jar 打包进 BRBE 核心 jar。fabric 端有
     *  fabric.mod.json entrypoint 会调 start()+collectAndInject()，但 neoforge
     *  端的 {@code BrbeJeiPluginsClientNeoForge.init()} 没有任何 @Mod 入口调用
     *  （neoforge.mods.toml 无 entrypoint、无 @Mod 类）——核心从不启动，registry
     *  恒空 → 查询引擎无 JEI 类别（anvil/brewing/grindstone/stonecutting/smithing/
     *  info 及全部 mod 类别缺失）。</p> */
    @SuppressWarnings("unchecked")
    /** 启动尝试闸：尝试过一次（无论成败）后不再重复调用 start()。headless
     *  核心在 atlas 未就绪时 start() 抛异常且 running 不置位——若不闸住，
     *  每 tick 都重试一次完整 JEI 启动（崩溃+日志），造成 1854 次/百秒 +
     *  662ms 帧尖峰（实测日志）。成功后已 running，天然不再触发；失败后
     *  依赖 atlas 就绪事件（RecipesUpdated/level load 重试一次）。 */
    private static boolean startAttempted;

    public static synchronized void ensureHeadlessStarted() {
        if (!available()) {
            return;
        }
        // JVM 盾：查询功能整体屏蔽（brbe.disableRecipeViewer=true，默认）时
        // 无头 JEI 一并禁用——不启动核心、不标记收集（viewer 屏蔽后其消费方
        // 停用，无头 JEI 仅在查询生态里有用；省去启动/收集成本）。
        if (com.alonie.brbe.config.RecipeViewerFeatureFlag.isDisabled()) {
            return;
        }
        if (startAttempted) {
            return;
        }
        try {
            if (headlessStartMethod != null && headlessIsRunningMethod != null) {
                boolean running = (boolean) headlessIsRunningMethod.invoke(null);
                startAttempted = true;
                if (!running) {
                    headlessStartMethod.invoke(null);
                    collectPending = true;
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            // start() 抛异常（如 atlas 未就绪）——startAttempted 已置位，不再每 tick
            // 重试完整 JEI 启动（否则卡顿）。真正启动由 atlas 就绪后的配方同步事件
            // 再次 requestCollect()+refresh() 时经 @see #retryStart() 触发。
            startAttempted = true;
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] headless-jei start failed (will retry on next recipe sync): {}", e.toString());
        }
    }

    /** 配方同步事件（atlas/配方就绪后）调用：重置 startAttempted 允许重试一次
     *  真正的启动，再由 refresh() 消费。 */
    public static synchronized void retryStart() {
        startAttempted = false;
        collectPending = true;
    }

    /** 标记下一次 {@link #refresh()} 需要重新收集 JEI 数据（配方同步事件调用）。
     *  由 refresh() 一次性消费并清位——不在每 tick 反复收集。 */
    public static synchronized void requestCollect() {
        collectPending = true;
    }

    /** 真正反射调用 {@code BrbeJeiPlugins.collectAndInject()}（仅 refresh() 在
     *  collectPending 时调用；重操作，不逐 tick 执行）。 */
    private static void doCollect() {
        if (pluginsCollectMethod == null) return;
        try {
            pluginsCollectMethod.invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] headless-jei collect failed: {}", e.toString());
        }
    }

    /** NeoForge：把 JEI GUI 图集（JeiGuiSpriteManager，即 PreparableReloadListener）
     *  注册到客户端资源重载 → atlas 才会初始化，headless 核心才能 start 成功。
     *  原 neoforge 入口 `BrbeJeiPluginsClientNeoForge.init()` 经
     *  RegisterClientReloadListenersEvent 注册——但该 init 在 NeoForge 无 @Mod 入口
     *  从不执行 → atlas 永不初始化 → 每次 start 抛 "atlas is not initialized"
     *  → 1854 次重试/百秒（卡顿根因）。这里 BRBE 反射补注册。
     *  @param registerClientReloadListeners  NeoForge RegisterClientReloadListenersEvent */
    public static void registerAtlasReloadListener(Object registerClientReloadListeners) {
        if (!available() || registerClientReloadListeners == null) return;
        try {
            // mezz.jei.common.Internal.getTextures().getGuiSpriteManager()
            Class<?> internal = Class.forName("mezz.jei.common.Internal");
            Object textures = internal.getMethod("getTextures").invoke(null);
            if (textures == null) return;
            Object spriteManager = textures.getClass().getMethod("getGuiSpriteManager").invoke(textures);
            if (spriteManager == null) return;
            // event.registerReloadListener(PreparableReloadListener)
            registerClientReloadListeners.getClass()
                    .getMethod("registerReloadListener",
                            Class.forName("net.minecraft.server.packs.resources.PreparableReloadListener"))
                    .invoke(registerClientReloadListeners, spriteManager);
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-BRIDGE] registered headless-jei GUI atlas reload listener");
        } catch (ReflectiveOperationException | LinkageError e) {
            BetterRecipeBook.LOGGER.debug("[BRBE-JEI-BRIDGE] atlas listener registration skipped: {}", e.toString());
        }
    }

    /** 引擎重建 → 清除可能失效的附着（holder 集已变化）并标记下一次
     *  refresh() 重新附着。 */
    private static synchronized void markAttachDirty() {
        ATTACHED_UID_BY_ID.clear();
        ATTACHED_RECIPE_BY_ID.clear();
        ATTACHED_ENTRY_BY_ID.clear();
        attachDirty = true;
    }

    /** 把引擎重建监听器接到桥（一次性；引擎 holder 通道重建晚于桥导入时
     *  保证附着重跑——attachVanillaLayouts 依赖引擎已有 holder 条目）。 */
    private static boolean rebuildListenerRegistered;

    private static void ensureRebuildListener() {
        if (rebuildListenerRegistered) return;
        rebuildListenerRegistered = true;
        RecipeViewerEngine.addRebuildListener(BrbeJeiBridge::markAttachDirty);
    }

    /** Re-import every JEI registry type into the query engine (called on
     *  JOIN / level load / engine rebuild / per-tick poll).  Idempotent. */
    @SuppressWarnings("unchecked")
    public static void refresh() {
        if (!available()) {
            return;
        }
        // JVM 盾：查询功能整体屏蔽 → 无头 JEI 一并禁用（不启动、不收集、不导入）。
        if (com.alonie.brbe.config.RecipeViewerFeatureFlag.isDisabled()) {
            return;
        }
        // 先确保核心已启动（幂等），再按 collectPending 决定是否重收集——收集是
        // 重操作（全量 JEI 索引），绝不能每 tick 执行（此前 662ms 卡顿根因）。
        // collectPending 由 start 转换/配方同步事件置位，这里消费一次后清位。
        ensureHeadlessStarted();
        ensureRebuildListener();
        boolean collect;
        synchronized (BrbeJeiBridge.class) {
            collect = collectPending;
            collectPending = false;
        }
        if (collect) {
            doCollect();
        }
        try {
            List<ResourceLocation> typeIds = (List<ResourceLocation>) typeIdsMethod.invoke(null);
            // 指纹：类型数 + 每类型条目数。注册表未变化时跳过整轮导入——使
            // 每 tick 轮询（1.21.11 语义）廉价。collectAndInject 幂等，故
            // 仅注册表内容真正变化时才重导入。
            StringBuilder sig = new StringBuilder(typeIds.size());
            for (ResourceLocation typeId : typeIds) {
                List<Object> raw = (List<Object>) entriesForMethod.invoke(null, typeId);
                sig.append(typeId).append(':').append(raw.size()).append(';');
            }
            String signature = sig.toString();
            synchronized (BrbeJeiBridge.class) {
                if (signature.equals(lastSignature) && !attachDirty) {
                    return;
                }
                lastSignature = signature;
                attachDirty = false;
            }
            int total = 0;
            for (ResourceLocation typeId : typeIds) {
                List<com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine.JeiEntry> entries =
                        new ArrayList<>();
                for (Object entry : (List<Object>) entriesForMethod.invoke(null, typeId)) {
                    // 槽位布局（headless-jei Entry 可选字段；缺席时 null → 弹窗回退
                    // 固定布局）。entry.slots 是 List<Entry.Slot(x,y,role,stacks)>，
                    // 各槽字段反射读取；layoutWidth/layoutHeight 同源。
                    List<RecipeViewerEngine.JeiSlot> slots = null;
                    int lw = 0;
                    int lh = 0;
                    try {
                        Object rawSlots = get(entry, "slots");
                        if (rawSlots instanceof List<?> slotList && !slotList.isEmpty()) {
                            slots = new java.util.ArrayList<>(slotList.size());
                            for (Object slot : slotList) {
                                List<ItemStack> stacks = (List<ItemStack>) get(slot, "stacks");
                                slots.add(new RecipeViewerEngine.JeiSlot(
                                        ((Number) get(slot, "x")).intValue(),
                                        ((Number) get(slot, "y")).intValue(),
                                        ((Number) get(slot, "role")).intValue(),
                                        stacks == null ? List.of() : stacks));
                            }
                            lw = ((Number) get(entry, "layoutWidth")).intValue();
                            lh = ((Number) get(entry, "layoutHeight")).intValue();
                        }
                    } catch (Exception ignored) {
                        // 无布局字段（老头/简化桥）——保持 null 回退
                    }
                    entries.add(new RecipeViewerEngine.JeiEntry(
                            (ResourceLocation) typeId,
                            get(entry, "recipe"),
                            (List<ItemStack>) get(entry, "inputs"),
                            (List<ItemStack>) get(entry, "outputs"),
                            slots, lw, lh));
                }
                if (entries.isEmpty()) continue;
                // 切石/锻造：条目由 RecipeViewerIndex（holder 通道）注册，
                // headless 的收集结果只用于给已有 holder 条目附着原生布局
                // （弹窗委托完整 JEI UI）——不重复导入 JEI 通道（类别 queryJei
                // 默认空，导入也无人消费；且会与 holder 通道数据并列）。
                if (ATTACH_TYPES.contains(typeId.toString())) {
                    attachVanillaLayouts(typeId, entries);
                    continue;
                }
                List<ItemStack> stations = (List<ItemStack>) stationsForMethod.invoke(null, typeId);
                RecipeViewerEngine.registerJeiType(typeId.toString(), entries,
                        stations == null ? List.of() : stations);
                registerPluginCategory(typeId, stations == null ? List.of() : stations);
                total += entries.size();
            }
            if (total > 0) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-BRIDGE] imported {} JEI entries from headless-jei ({} types)",
                        total, typeIds.size());
            }
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] import failed: {}", e.toString());
        }
    }

    /** BRBE 内置类别（RecipeViewerCategories.BUILTIN）不重复注册。 */
    private static final java.util.Set<String> BUILTIN_CATEGORY_TYPES =
            java.util.Set.of("minecraft:crafting", "minecraft:smelting", "minecraft:blasting",
                    "minecraft:smoking", "minecraft:campfire_cooking",
                    "minecraft:stonecutting", "minecraft:smithing",
                    "minecraft:anvil", "minecraft:brewing", "minecraft:grindstone",
                    "minecraft:compostable");

    /** 把 headless registry 的一个 mod JEI 类型注册为 BRBE 查询类别 tab。 */
    private static void registerPluginCategory(ResourceLocation typeId, List<ItemStack> stations) {
        try {
            String uid = typeId.toString();
            if (BUILTIN_CATEGORY_TYPES.contains(uid)) return;
            String title = titleForMethod == null ? null : (String) titleForMethod.invoke(null, typeId);
            net.minecraft.network.chat.Component titleText = title == null || title.isBlank()
                    ? net.minecraft.network.chat.Component.literal(typeId.getPath())
                    : net.minecraft.network.chat.Component.literal(title);
            com.alonie.brbe.recipeviewer.RecipeViewerCategories.registerExternal(
                    List.of(new com.alonie.brbe.recipeviewer.PluginRecipeViewerCategory(
                            List.of(uid), titleText, stations)));
        } catch (Exception | LinkageError e) {
            // 类别注册失败不阻断数据导入
        }
    }

    /** 切石/锻造：把 headless 收集的 JEI 条目布局附着到引擎已有的 holder
     *  条目上（按 {@code holder.id()} 恒等匹配——同一 RecipeManager 数据源）。
     *  附着后 holder 条目的 Shift 预览/pin 可委托完整 JEI UI（getLayout
     *  非空 → 1:1 委托路径）。 */
    private static void attachVanillaLayouts(ResourceLocation typeId, List<JeiEntry> entries) {
        try {
            String uid = typeId.toString();
            List<RecipeHolder<?>> all = RecipeViewerEngine.allRecipes(uid);
            if (all.isEmpty()) return;
            Map<String, RecipeHolder<?>> byId = new HashMap<>();
            for (RecipeHolder<?> holder : all) {
                byId.put(holder.id().toString(), holder);
            }
            int attached = 0;
            for (JeiEntry jei : entries) {
                if (jei.layoutWidth() <= 0 || jei.layoutHeight() <= 0) continue;
                if (!(jei.recipe() instanceof RecipeHolder<?> holder)) continue;
                RecipeHolder<?> existing = byId.get(holder.id().toString());
                if (existing == null) continue;
                List<RecipeSlotLayout> slotLayouts = new ArrayList<>();
                if (jei.slots() != null) {
                    for (JeiSlot slot : jei.slots()) {
                        slotLayouts.add(new RecipeSlotLayout(slot.x(), slot.y(), slot.role(),
                                slot.stacks() == null ? List.of() : slot.stacks()));
                    }
                }
                RecipeDisplayId id = RecipeViewerEngine.idFor(existing);
                RecipeViewerEngine.registerLayout(id, new RecipeLayout(
                        jei.layoutWidth(), jei.layoutHeight(), slotLayouts, null));
                ATTACHED_UID_BY_ID.put(id, typeId);
                ATTACHED_RECIPE_BY_ID.put(id, jei.recipe());
                ATTACHED_ENTRY_BY_ID.put(id, jei);
                attached++;
            }
            if (attached > 0) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-BRIDGE] attached vanilla JEI layout to {} {} entries",
                        attached, uid);
            }
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] attachVanillaLayouts failed: {}", e.toString());
        }
    }

    /** holder 条目附着后的完整 JeiEntry（弹窗 1:1 委托直接用），或 null。 */
    public static JeiEntry attachedJeiEntry(RecipeDisplayId id) {
        return id == null ? null : ATTACHED_ENTRY_BY_ID.get(id);
    }

    private static Object get(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }
}
