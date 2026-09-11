package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.jei.plugins.engine.SyntheticRecipeDisplayEntryFactory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.21.11 无头 JEI 桥（BRBE 侧，纯反射）：独立项目 headless-jei mod 的
 * {@code JeiRecipeRegistry}（轻量桥）条目 → BRBE 查询引擎（display 版）。
 *
 * <p>headless-jei 产物按 intermediary 映射发布（与 BRBE 核心 jar 一致），
 * 其桥 API 无法直接用于 BRBE 的 official 编译——改用反射（与 JeiHudHider
 * 同模式）；headless-jei mod 缺席（真实 JEI 或纯原版）时所有调用静默跳过。
 * 渲染侧 {@link SyntheticRecipeRendererImpl} 经
 * {@link #reflectRecipeManager} / {@link #reflectCategory} /
 * {@link #emptyFocusGroup} 反射复用 headless-jei 的 JEI 运行时。</p>
 */
public final class BrbeJeiBridge {

    private BrbeJeiBridge() {}

    private static Class<?> registryClass;
    private static Method typeIdsMethod;
    private static Method entriesForMethod;
    private static Method stationsForMethod;
    private static Method titleForMethod;
    private static Class<?> runtimeBridgeClass;
    private static Method runtimeBridgeRecipeManager;
    private static Class<?> indexerClass;
    private static Method indexerCategoryFor;
    private static Class<?> emptyFocusGroupClass;

    /** Synthetic display id → JEI type uid / raw recipe (渲染委托用)。 */
    private static final Map<RecipeDisplayId, Identifier> UID_BY_ID = new HashMap<>();
    private static final Map<RecipeDisplayId, Object> RECIPE_BY_ID = new HashMap<>();

    /** 上次导入的 registry 指纹（类型数 + 条目数）。headless 收集分两阶段：
     *  首轮只有 vanilla 类别，同步事件到达后 mod 配方才写入 registry——
     *  指纹变化时 tick 轮询会再次 refresh（registerType 幂等）。 */
    private static long lastFingerprint = -1;

    /** [BRBE fork] Real JEI's "pause recipe cycling" freezes the delegated
     *  drawables of BRBE's preview / pin while its key is held — real JEI
     *  30.24 defaults it to LEFT SHIFT, exactly BRBE's preview hotkey, so the
     *  preview/pin items freeze while Shift is held.  BRBE removed that
     *  behaviour: the vendored fork's CycleTicker/CycleTimer pause on ALT only
     *  (binding-independent GLFW reads), so for the real runtime the mapping
     *  is unbound once at runtime ({@code setKey(UNKNOWN)}) — retrofits any
     *  options.txt binding without touching the instance config. */
    private static volatile boolean cyclePauseUnbound;
    private static Method keyMappingsProvider;
    private static Method pauseCyclingGetter;
    private static Method keyMappingGetter;

    public static void unbindJeiCyclePause() {
        if (cyclePauseUnbound) return;
        try {
            if (keyMappingsProvider == null) {
                keyMappingsProvider = Class.forName("mezz.jei.common.Internal")
                        .getMethod("getKeyMappings");
            }
            Object mappings = keyMappingsProvider.invoke(null);
            if (mappings == null) return;
            if (pauseCyclingGetter == null) {
                pauseCyclingGetter = mappings.getClass().getMethod("getPauseRecipeCycling");
            }
            Object pause = pauseCyclingGetter.invoke(mappings);
            if (pause == null) return;
            // getMapping() is protected on the abstract mapping impl — walk the
            // hierarchy for the declaring class (real JEI 30.24 and the fork
            // both declare it on their concrete mapping classes).
            Class<?> cls = pause.getClass();
            while (cls != null && keyMappingGetter == null) {
                try {
                    keyMappingGetter = cls.getDeclaredMethod("getMapping");
                } catch (NoSuchMethodException e) {
                    cls = cls.getSuperclass();
                }
            }
            if (keyMappingGetter == null) return;
            keyMappingGetter.setAccessible(true);
            Object keyMapping = keyMappingGetter.invoke(pause);
            if (keyMapping instanceof net.minecraft.client.KeyMapping km) {
                km.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
                cyclePauseUnbound = true;
            }
        } catch (Throwable ignored) {
            // JEI runtime / mappings not up yet — retried on the next tick
        }
    }

    /** Whether the headless-jei mod is present on the classpath. */
    public static synchronized boolean jeiAvailable() {
        if (registryClass != null) {
            return true;
        }
        try {
            registryClass = Class.forName("com.alonie.brbe.jei.api.JeiRecipeRegistry");
            typeIdsMethod = registryClass.getMethod("typeIds");
            entriesForMethod = registryClass.getMethod("entriesFor", Identifier.class);
            stationsForMethod = registryClass.getMethod("stationsFor", Identifier.class);
            titleForMethod = registryClass.getMethod("titleFor", Identifier.class);
            runtimeBridgeClass = Class.forName("com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge");
            runtimeBridgeRecipeManager = runtimeBridgeClass.getMethod("recipeManager");
            indexerClass = Class.forName("com.alonie.brbe.jei.plugins.engine.PluginRecipeIndexer");
            indexerCategoryFor = indexerClass.getMethod("categoryFor", Identifier.class);
            emptyFocusGroupClass = Class.forName("com.alonie.brbe.jei.plugins.engine.EmptyFocusGroup");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            registryClass = null;
            return false;
        }
    }

    /** The JEI runtime's recipe manager (headless-jei), or null. */
    public static Object reflectRecipeManager() {
        if (!jeiAvailable()) return null;
        try {
            return runtimeBridgeRecipeManager.invoke(null);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** The JEI recipe category of a synthetic entry's type (headless-jei), or null. */
    public static Object reflectCategory(RecipeDisplayId id) {
        if (!jeiAvailable()) return null;
        Identifier uid = UID_BY_ID.get(id);
        if (uid == null) return null;
        try {
            return indexerCategoryFor.invoke(null, uid);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** The empty focus group singleton (headless-jei), or null. */
    public static Object emptyFocusGroup() {
        if (!jeiAvailable()) return null;
        try {
            return emptyFocusGroupClass.getField("INSTANCE").get(null);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** Raw JEI recipe object of a synthetic entry (render delegation), or null. */
    public static Object recipeFor(RecipeDisplayId id) {
        return RECIPE_BY_ID.get(id);
    }

    /** Type uid of a synthetic entry, or null. */
    public static Identifier uidFor(RecipeDisplayId id) {
        return UID_BY_ID.get(id);
    }

    /** 把 synthetic 条目的渲染数据（JEI 配方对象 + 类型 uid）复制到书驱动
     *  条目 id 下：查询命中被解析为书驱动条目后，弹窗/预览的 JEI 委托
     *  （{@link #recipeFor}）与类型查询（{@link #uidFor}）继续可用。 */
    public static void migrateRecipeData(RecipeDisplayId from, RecipeDisplayId to) {
        if (from == null || to == null || from.equals(to)) return;
        Object recipe = RECIPE_BY_ID.get(from);
        if (recipe != null) {
            RECIPE_BY_ID.put(to, recipe);
        }
        Identifier uid = UID_BY_ID.get(from);
        if (uid != null) {
            UID_BY_ID.put(to, uid);
        }
    }

    /** Re-import every JEI registry type into the query engine (JOIN / 引擎重建)。 */
    @SuppressWarnings("unchecked")
    public static void refresh() {
        // 真实 JEI 的"暂停配方轮循"会使 BRBE 预览/pin 委托渲染的 JEI drawable
        // 在按住其暂停键时冻结——真实 JEI 30.24 默认/实例绑定都是 LEFT SHIFT，
        // 正是 BRBE 的预览键。运行时解绑一次（fork 的 CycleTicker/CycleTimer
        // 已改 ALT-only 直读，不依赖此映射）。
        unbindJeiCyclePause();
        if (!jeiAvailable()) {
            return;
        }
        // 真实 JEI / 无头场景统一：数据由 headless 侧收集写进 JeiRecipeRegistry
        // （真实 JEI 存在时收集由 BrbeJeiPlugins 从真实 runtime 搬运，入口
        // BrbeJeiPluginsClientFabric 的 real-JEI 分支），这里只做 registry →
        // 引擎导入。渲染委托经读取制 JeiRuntimeBridge（真实 JEI 的 runtime）。
        try {
            List<Identifier> typeIds = (List<Identifier>) typeIdsMethod.invoke(null);
            // 指纹排重：headless 收集分两阶段（vanilla → mod），registry
            // 每次 replace 全量替换；指纹未变则跳过（避免每 tick 重复导入）。
            long typeTotal = 0;
            for (Identifier t : typeIds) {
                List<Object> es = (List<Object>) entriesForMethod.invoke(null, t);
                typeTotal += es == null ? 0 : es.size();
            }
            long fingerprint = (long) typeIds.size() * 1_000_000L + typeTotal;
            // 原版类型的 mod 工作站数量也计入指纹：仅 stations 变化（mod
            // 站集合不同）时同样触发重导入（工作台表刷新）。
            for (String uid : VANILLA_STATION_TYPES.keySet()) {
                Object st = stationsForMethod.invoke(null, Identifier.parse(uid));
                if (st instanceof List<?> l) {
                    fingerprint = fingerprint * 31 + l.size();
                }
            }
            if (fingerprint == lastFingerprint) {
                return;
            }
            lastFingerprint = fingerprint;
            UID_BY_ID.clear();
            RECIPE_BY_ID.clear();
            int total = 0;
            for (Identifier typeId : typeIds) {
                List<Object> entries = (List<Object>) entriesForMethod.invoke(null, typeId);
                if (entries == null || entries.isEmpty()) continue;
                List<ItemStack> stations = (List<ItemStack>) stationsForMethod.invoke(null, typeId);
                if (stations == null) stations = List.of();

                List<RecipeViewerEngine.IndexedRecipe> indexed = new ArrayList<>();
                for (Object entry : entries) {
                    Identifier uid = (Identifier) typeId;
                    Object recipe = get(entry, "recipe");
                    List<ItemStack> inputs = (List<ItemStack>) get(entry, "inputs");
                    List<ItemStack> outputs = (List<ItemStack>) get(entry, "outputs");
                    if (inputs == null) inputs = List.of();
                    if (outputs == null) outputs = List.of();
                    RecipeDisplayEntry displayEntry =
                            SyntheticRecipeDisplayEntryFactory.createForItemLists(inputs, stations, outputs);
                    indexed.add(new RecipeViewerEngine.IndexedRecipe(displayEntry, inputs, outputs));
                    UID_BY_ID.put(displayEntry.id(), uid);
                    RECIPE_BY_ID.put(displayEntry.id(), recipe);

                    // 原生布局（mod 配方槽位/尺寸）注册给引擎，渲染器需要。
                    List<RecipeViewerEngine.RecipeSlotLayout> slotLayouts = new ArrayList<>();
                    Object slots = get(entry, "slots");
                    if (slots instanceof List<?> slotList) {
                        for (Object slot : slotList) {
                            int x = ((Number) get(slot, "x")).intValue();
                            int y = ((Number) get(slot, "y")).intValue();
                            int role = ((Number) get(slot, "role")).intValue();
                            List<ItemStack> stacks = (List<ItemStack>) get(slot, "stacks");
                            slotLayouts.add(new RecipeViewerEngine.RecipeSlotLayout(x, y, role,
                                    stacks == null ? List.of() : stacks));
                        }
                    }
                    int layoutW = ((Number) get(entry, "layoutWidth")).intValue();
                    int layoutH = ((Number) get(entry, "layoutHeight")).intValue();
                    if (layoutW <= 0 || layoutH <= 0) {
                        // setRecipe 提取失败（如 smithing trim 的 tag 未绑定）：
                        // 从 JEI 类别取类别尺寸作为兜底 layout（槽位空），保证
                        // canRender → 弹窗委托完整 JEI UI（drawable 自带槽位）。
                        int[] size = categorySize(typeId);
                        layoutW = size[0];
                        layoutH = size[1];
                    }
                    if (layoutW > 0 && layoutH > 0) {
                        RecipeViewerEngine.registerLayout(displayEntry.id(),
                                new RecipeViewerEngine.RecipeLayout(layoutW, layoutH, slotLayouts, null));
                    }
                }
                if (indexed.isEmpty()) continue;
                String uidStr = typeId.toString();
                // 锻造：数据由 BRBE 侧（配方书已知集）权威采集，headless 不再
                // registerType 覆盖——只把 headless 收集的 JEI 原生 layout 挂到
                // BRBE 已注册条目上，供弹窗委托完整 JEI UI。
                // 酿造：headless 直接注册（条目自带 native layout——委托完整 JEI
                // UI 无需匹配；解锁门控在查询类别侧）。
                if (uidStr.equals("minecraft:smithing")) {
                    attachVanillaLayouts(typeId, entries, stations);
                    continue;
                }
                // 切石：条目与 layout 均由 headless 提供（BRBE 侧跳过），照常注册。
                RecipeViewerEngine.registerType(uidStr, indexed, stations);
                // mod 类型（非 BRBE 内置 10 类）注册为动态查询类别 tab
                registerPluginCategory(typeId, stations);
                total += indexed.size();
            }
            if (total > 0) {
                // 内嵌时代由 BrbeJeiPlugins 注册；独立化后该处被删除，
                // SyntheticRecipeRenderers 恒为 NONE → 弹窗永远走 vanilla
                // 兜底，无法委托 JEI 界面。这里在数据导入成功时注册渲染器
                // （与 SRImpl 同 jar，无编译问题；幂等）。
                if (com.alonie.brbe.compat.SyntheticRecipeRenderers.get()
                        == com.alonie.brbe.compat.SyntheticRecipeRenderer.NONE) {
                    com.alonie.brbe.compat.SyntheticRecipeRenderers.register(
                            new com.alonie.brbe.jei.plugins.engine.SyntheticRecipeRendererImpl());
                }
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-BRIDGE] imported {} JEI entries from headless-jei ({} types)",
                        total, typeIds.size());
            }
            // 注册到原版类型的 mod 工作站（如 BetterEnd 末地石冶炼炉 →
            // minecraft:blasting）：registry 的 stations 不依赖条目存在，
            // 主侧工作站表（烧炼行/查询命中）据此合并。
            importVanillaStationSpecs();
            // 锻造类别由 BRBE 侧（配方书已知集）权威注册，其 native layout 需
            // 在 rebuild（flushEngineRebuildIfDirty）之后挂到重建的条目上——
            // 而 refresh() 先于 rebuild 执行（此时 allRecipes(minecraft:smithing)
            // 为空，attachVanillaLayouts 挂不上）。注册一个 rebuild 监听器，在
            // 每次 rebuild 完成后重新挂 smithing layout，供弹窗委托完整 JEI UI。
            registerLayoutRebuildListener();
            // 指纹重导入清了 UID_BY_ID/RECIPE_BY_ID（酿造/锻造条目不在本轮
            // registerType 内重挂——它们在 rebuild 尾部由 reattachBookLayouts
            // 挂回）。强制下一次引擎重建，保证该映射立即收敛。
            RecipeViewerIndex.forceNextRebuild();
            // 进度模式合法工作站集重建（mod 类型刚注册完毕；引擎侧 vanilla 类型
            // 由 rebuildEngineInternal 尾部再建一次，幂等）。
            RecipeViewerIndex.rebuildProgressStationItems();
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] import failed: {}", e.toString());
        }
    }

    /** 幂等注册：每次配方书 rebuild（notifyRebuilt）后重新挂 smithing native
     *  layout（BRBE 侧注册的条目 id 随 rebuild 重建，layout 需同步重挂）。 */
    private static volatile boolean layoutRebuildListenerRegistered;

    private static void registerLayoutRebuildListener() {
        if (layoutRebuildListenerRegistered) return;
        layoutRebuildListenerRegistered = true;
        RecipeViewerEngine.registerRebuildListener(BrbeJeiBridge::reattachBookLayouts);
    }

    /** 对锻造（数据由配方书侧权威供给的类型）重新按 headless 收集的
     *  JEI 原生 layout 挂到引擎当前条目上（rebuild 后调用；条目已就绪时才能
     *  匹配挂接）。酿造条目由 headless 直接注册（自带 layout），无需挂接。 */
    public static void reattachBookLayouts() {
        try {
            if (!jeiAvailable()) return;
            reattachTypeLayouts("minecraft:smithing");
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] reattachBookLayouts failed: {}", e.toString());
        }
    }

    private static void reattachTypeLayouts(String typeIdStr) throws Exception {
        Identifier typeId = Identifier.parse(typeIdStr);
        List<Object> entries = (List<Object>) entriesForMethod.invoke(null, typeId);
        if (entries == null || entries.isEmpty()) return;
        List<ItemStack> stations = (List<ItemStack>) stationsForMethod.invoke(null, typeId);
        attachVanillaLayouts(typeId, entries, stations == null ? List.of() : stations);
    }

    /** 原版类型 → (Family 名, 配方书路径前缀)：mod 工作站注册到这些类型时
     *  按原版类别路径显示（烧炼行分组/查询命中）。 */
    private static final Map<String, String[]> VANILLA_STATION_TYPES = Map.of(
            "minecraft:smelting", new String[] {"FURNACE", "furnace_"},
            "minecraft:blasting", new String[] {"FURNACE", "blast_furnace_"},
            "minecraft:smoking", new String[] {"FURNACE", "smoker_"},
            "minecraft:campfire_cooking", new String[] {"FURNACE", "campfire"},
            "minecraft:crafting", new String[] {"CRAFTING", "crafting_"},
            "minecraft:stonecutting", new String[] {"STONECUTTING", "stonecutter"},
            "minecraft:smithing", new String[] {"SMITHING", "smithing"},
            "minecraft:anvil", new String[] {"ANVIL", "anvil"},
            "minecraft:brewing", new String[] {"BREWING", "brewing"},
            "minecraft:grindstone", new String[] {"GRINDSTONE", "grindstone"});

    /** 从 registry 拉取原版类型的 mod 工作站并注册进 BRBE 工作站表
     *  （registerExternalWorkstations 幂等：同 typeId 覆盖、同内容跳过）。 */
    private static void importVanillaStationSpecs() {
        try {
            List<RecipeViewerIndex.WorkstationSpec> specs = new ArrayList<>();
            for (Map.Entry<String, String[]> e : VANILLA_STATION_TYPES.entrySet()) {
                Object stations = stationsForMethod.invoke(null, Identifier.parse(e.getKey()));
                if (!(stations instanceof List<?> list) || list.isEmpty()) continue;
                List<String> items = new ArrayList<>();
                for (Object s : list) {
                    if (s instanceof ItemStack stack && !stack.isEmpty()) {
                        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(stack.getItem());
                        // 内置工作站（熔炉/铁砧/酿造台/研磨石…）已在 BUILTIN 表，
                        // registry 的 vanilla 运行时站与它们重复——去重。
                        if (RecipeViewerIndex.builtinWorkstationItemIds().contains(id)) {
                            continue;
                        }
                        items.add(id.toString());
                    }
                }
                if (items.isEmpty()) continue;
                specs.add(new RecipeViewerIndex.WorkstationSpec(
                        e.getValue()[0], e.getKey(), List.of(e.getValue()[1]), items));
            }
            if (!specs.isEmpty()) {
                RecipeViewerIndex.registerExternalWorkstations(specs);
                // 工作站表变化 → 引擎重算（烧炼行图标/usage 命中）。
                RecipeViewerIndex.forceNextRebuild();
            }
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] importVanillaStationSpecs failed: {}", e.toString());
        }
    }

    /** 经 headless 类别（反射）取类别槽位区域尺寸（兜底 layout 用）。 */
    private static final java.util.Set<String> BUILTIN_CATEGORY_TYPES =
            java.util.Set.of("minecraft:crafting", "minecraft:smelting", "minecraft:blasting",
                    "minecraft:smoking", "minecraft:campfire_cooking",
                    "minecraft:stonecutting", "minecraft:smithing",
                    "minecraft:anvil", "minecraft:brewing", "minecraft:grindstone",
                    "minecraft:compostable");

    /** 切石/锻造：条目已由 RecipeViewerIndex 注册；这里按 display 等价
     *  把 headless 收集到的 JEI 原生 layout 挂到引擎已有条目上，并登记
     *  UID_BY_ID/RECIPE_BY_ID 供渲染委托（弹窗 → 完整 JEI UI）。
     *
     *  <p>锻造的纹饰（trim）配方 headless 侧收集不到（tag 未绑定时机导致
     *  setRecipe 失败被丢弃，见 {@link #attachSmithingFallbackLayouts}），
     *  由 BRBE 侧按固定几何兜底挂接（与 transform 同一类别/同一 layout）。 */
    private static void attachVanillaLayouts(Identifier typeId,
                                             List<Object> entries,
                                             List<ItemStack> stations) {
        try {
            int attached = 0;
            java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> all =
                    RecipeViewerEngine.allRecipes(typeId.toString());
            for (Object entry : entries) {
                Object recipe = get(entry, "recipe");
                // headless 收集到的 entry 的 recipe 是 RecipeHolder（datapack）；
                // RecipeHolder.value() 返回 Recipe（SmithingRecipe），其
                // display() 才是 RecipeDisplay——按此提取并与引擎条目匹配。
                net.minecraft.world.item.crafting.display.RecipeDisplay targetDisplay = null;
                if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
                        && holder.value() instanceof net.minecraft.world.item.crafting.Recipe<?> srcRecipe) {
                    try {
                        List<net.minecraft.world.item.crafting.display.RecipeDisplay> displays =
                                srcRecipe.display();
                        if (!displays.isEmpty()) targetDisplay = displays.get(0);
                    } catch (Exception | LinkageError ignored) {
                        // display() unresolvable
                    }
                }
                if (targetDisplay == null) continue;
                // [DEBUG-layout] Temporary: report display classes for matching.
                if (!all.isEmpty()) {
                    BetterRecipeBook.LOGGER.warn("[DEBUG-layout] match target={} firstEngine={} equal(engine1)={}",
                            targetDisplay.getClass().getSimpleName(),
                            all.get(0).display() == null ? "null" : all.get(0).display().getClass().getSimpleName(),
                            all.get(0).display() != null && all.get(0).display().equals(targetDisplay));
                }
                int layoutW = ((Number) get(entry, "layoutWidth")).intValue();
                int layoutH = ((Number) get(entry, "layoutHeight")).intValue();
                if (layoutW <= 0 || layoutH <= 0) continue;
                List<RecipeViewerEngine.RecipeSlotLayout> slotLayouts = new ArrayList<>();
                Object slots = get(entry, "slots");
                if (slots instanceof List<?> slotList) {
                    for (Object slot : slotList) {
                        int x = ((Number) get(slot, "x")).intValue();
                        int y = ((Number) get(slot, "y")).intValue();
                        int role = ((Number) get(slot, "role")).intValue();
                        List<ItemStack> stacks = (List<ItemStack>) get(slot, "stacks");
                        slotLayouts.add(new RecipeViewerEngine.RecipeSlotLayout(x, y, role,
                                stacks == null ? List.of() : stacks));
                    }
                }
                for (RecipeDisplayEntry existing : all) {
                    if (existing.display() != null
                            && existing.display().equals(targetDisplay)) {
                        RecipeViewerEngine.registerLayout(existing.id(),
                                new RecipeViewerEngine.RecipeLayout(layoutW, layoutH, slotLayouts, null));
                        UID_BY_ID.put(existing.id(), typeId);
                        RECIPE_BY_ID.put(existing.id(), recipe);
                        attached++;
                        break;
                    }
                }
            }
            // 锻造：headless 收集不到的 trim 条目（tag 绑定晚于收集时机、
            // setRecipe 失败被整体丢弃）由 BRBE 侧兜底挂接——布局用锻造
            // 类别固定几何（与 transform 同类别），RecipeHolder 从客户端
            // 同步配方（fabric SynchronizedRecipes）按 display 等价反查。
            // （酿造不再需要：条目由 headless 直接注册、自带 native layout。）
            int fallback = 0;
            if (typeId.toString().equals("minecraft:smithing")) {
                fallback = attachSmithingFallbackLayouts(all);
            }
            if (attached > 0 || fallback > 0) {
                BetterRecipeBook.LOGGER.info(
                        "[BRBE-JEI-BRIDGE] attached vanilla JEI layout to {} stonecutter/smithing entries ({}+{})",
                        attached + fallback, attached, fallback);
            }
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] attachVanillaLayouts failed: {}", e.toString());
        }
    }

    /** 原版锻造类别固定几何（vendored mezz {@code SmithingRecipeCategory}：
     *  template (1,6) / base (19,6) / addition (37,6) / output (91,6)，
     *  108x28——transform 与 trim 共用同一类别与 layout）。fallback 挂接用
     *  （槽位 stacks 留空：委托渲染时由真实 JEI drawable 自绘槽位内容）。 */
    private static final RecipeViewerEngine.RecipeLayout SMITHING_LAYOUT =
            new RecipeViewerEngine.RecipeLayout(108, 28, List.of(
                    new RecipeViewerEngine.RecipeSlotLayout(1, 6, 0, List.of()),
                    new RecipeViewerEngine.RecipeSlotLayout(19, 6, 0, List.of()),
                    new RecipeViewerEngine.RecipeSlotLayout(37, 6, 0, List.of()),
                    new RecipeViewerEngine.RecipeSlotLayout(91, 6, 1, List.of())), null);

    /** 客户端同步配方（fabric {@code ClientRecipeSynchronizedEvent} 回调给的
     *  {@code SynchronizedRecipes}）：引擎仅存 RecipeDisplayEntry（display 数据），
     *  委托渲染还需要原始 RecipeHolder（喂给 JEI 的 createRecipeLayoutDrawable）
     *  ——按 display 值等价从同步配方反查（与 headless 匹配同一谓词）。 */
    private static volatile net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes clientSyncedRecipes;
    private static volatile boolean syncedRecipesListenerRegistered;
    /** headless 侧同一事件已存储的实例（其监听在 mod init 注册，早于登录）——
     *  本桥监听若晚于事件注册会错过回调，反射兜底读取。 */
    private static Method syncedRecipesProviderMethod;

    /** Client init 时调用：尽早注册同步配方监听（须先于登录的
     *  {@code ClientRecipeSynchronizedEvent}，否则错过回调、兜底永远无数据）。 */
    public static void initClient() {
        registerSyncedRecipesListener();
    }

    private static void registerSyncedRecipesListener() {
        if (syncedRecipesListenerRegistered) return;
        synchronized (BrbeJeiBridge.class) {
            if (syncedRecipesListenerRegistered) return;
            syncedRecipesListenerRegistered = true;
            try {
                net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent.EVENT.register(
                        (minecraft, synced) -> clientSyncedRecipes = synced);
            } catch (Exception | LinkageError e) {
                // fabric-recipe-api 缺席：fallback 静默失效（不影响既有路径）
            }
        }
    }

    /** 同步配方实例：优先本桥监听捕获；未捕获时反射读 headless 已存的同实例
     *  （其监听注册更早，事件必然捕获）。仍为空说明数据尚未同步完成。 */
    private static net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes resolveSyncedRecipes() {
        if (clientSyncedRecipes != null) return clientSyncedRecipes;
        if (syncedRecipesProviderMethod == null) {
            try {
                syncedRecipesProviderMethod = Class.forName(
                        "com.alonie.brbe.jei.plugins.BrbeJeiPlugins")
                        .getMethod("syncedRecipes");
            } catch (Exception | LinkageError e) {
                // headless 缺席：无法反射获取
                syncedRecipesProviderMethod = null;
            }
        }
        if (syncedRecipesProviderMethod != null) {
            try {
                Object value = syncedRecipesProviderMethod.invoke(null);
                if (value instanceof net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes synced) {
                    clientSyncedRecipes = synced;
                }
            } catch (Exception | LinkageError ignored) {
            }
        }
        return clientSyncedRecipes;
    }

    /** 引擎里所有没挂上 layout 的锻造条目（trim/mod 配方）：挂固定几何 layout +
     *  display 等价的 RecipeHolder（缺失 holder 的条目跳过，保持 vanilla
     *  兜底渲染）。优先按 display id 从集成服务器配方管理器 1:1 反查，再按
     *  display 值等价匹配。返回挂接数。 */
    private static int attachSmithingFallbackLayouts(
            List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> all) {
        registerSyncedRecipesListener();
        resolveSyncedRecipes();
        if (all.isEmpty()) return 0;
        int pending = 0;
        for (net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry : all) {
            if (RecipeViewerEngine.getLayout(entry.id()) == null) pending++;
        }
        int out = 0;
        if (pending > 0) {
            for (net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry : all) {
                if (RecipeViewerEngine.getLayout(entry.id()) != null) continue;
                Object holder = findSmithingHolderById(entry);
                if (holder == null) holder = findSmithingHolder(entry);
                if (holder == null) continue;
                RecipeViewerEngine.registerLayout(entry.id(), SMITHING_LAYOUT);
                UID_BY_ID.put(entry.id(), Identifier.parse("minecraft:smithing"));
                RECIPE_BY_ID.put(entry.id(), holder);
                out++;
            }
            // [DEBUG-fb] Temporary: report fallback source state when nothing attached
            // (rate-limited to 5s to avoid per-tick spam from the poll).
            if (out == 0) {
                long now = System.currentTimeMillis();
                if (now - lastFbDebugLog > 5_000) {
                    lastFbDebugLog = now;
                    StringBuilder sample = new StringBuilder();
                    int shown = 0;
                    for (net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry : all) {
                        if (RecipeViewerEngine.getLayout(entry.id()) != null) continue;
                        if (shown++ >= 4) break;
                        sample.append(entry.display() == null ? "null"
                                : entry.display().getClass().getSimpleName())
                                .append("(byId=").append(findSmithingHolderById(entry) != null).append("),");
                    }
                    BetterRecipeBook.LOGGER.warn(
                            "[DEBUG-fb] smithing fallback: entries={} pending={} synced={} smithingHolders={} serverSmithingHolders={} sample=[{}]",
                            all.size(), pending, clientSyncedRecipes != null,
                            smithingHolders().size(), serverSmithingHolders().size(), sample);
                }
            }
        }
        return out;
    }

    /** [DEBUG-fb] 日志限频（轮询每 tick 调用，避免刷屏）。 */
    private static long lastFbDebugLog;


    /** 同步配方的锻造 holder 缓存（keyed by SynchronizedRecipes 实例）：全量
     *  同步 set 很大（数千配方），避免每 tick 全量扫描——只过滤一次缓存。 */
    private static volatile java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> smithingHolderCache =
            java.util.List.of();
    private static volatile Object smithingHolderCacheSource;

    private static java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> smithingHolders() {
        net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes synced = resolveSyncedRecipes();
        if (synced == null) return java.util.List.of();
        if (smithingHolderCacheSource == synced) return smithingHolderCache;
        synchronized (BrbeJeiBridge.class) {
            if (smithingHolderCacheSource == synced) return smithingHolderCache;
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> list = new java.util.ArrayList<>();
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : synced.recipes()) {
                if (holder != null
                        && holder.value() instanceof net.minecraft.world.item.crafting.SmithingRecipe) {
                    list.add(holder);
                }
            }
            smithingHolderCache = java.util.List.copyOf(list);
            smithingHolderCacheSource = synced;
        }
        return smithingHolderCache;
    }

    /** 单机：集成服务器配方管理器 = 全量服务器配方（vanilla + mod），且
     *  {@code getRecipeFromDisplay} 按 display id 1:1 映射到 RecipeHolder
     *  （ServerDisplayInfo.parent）——比 fabric 同步集可靠（26.2 单机实测
     *  同步集 recipes() 为空：smithingHolders()=0）。LAN/多机无集成服务器
     *  时返回 null，走同步集兜底。 */
    private static Object findSmithingHolderById(
            net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.getSingleplayerServer() == null) return null;
            Object info = mc.getSingleplayerServer().getRecipeManager()
                    .getRecipeFromDisplay(entry.id());
            if (info instanceof net.minecraft.world.item.crafting.RecipeManager.ServerDisplayInfo sdi) {
                return sdi.parent();
            }
        } catch (Exception | LinkageError e) {
            // server recipe manager unavailable
        }
        return null;
    }

    /** 集成服务器配方的锻造 holder 缓存（keyed by RecipeManager 实例）：
     *  getRecipes() 全量过滤一次；负 id 的本地缓存条目没有 display 同步 id，
     *  只能走 display 值等价匹配此源。 */
    private static volatile java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> serverSmithingCache =
            java.util.List.of();
    private static volatile Object serverSmithingCacheSource;

    private static java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> serverSmithingHolders() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.getSingleplayerServer() == null) return java.util.List.of();
            net.minecraft.world.item.crafting.RecipeManager mgr =
                    mc.getSingleplayerServer().getRecipeManager();
            if (serverSmithingCacheSource == mgr) return serverSmithingCache;
            synchronized (BrbeJeiBridge.class) {
                if (serverSmithingCacheSource == mgr) return serverSmithingCache;
                java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> list = new java.util.ArrayList<>();
                for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : mgr.getRecipes()) {
                    if (holder != null
                            && holder.value() instanceof net.minecraft.world.item.crafting.SmithingRecipe) {
                        list.add(holder);
                    }
                }
                serverSmithingCache = java.util.List.copyOf(list);
                serverSmithingCacheSource = mgr;
            }
        } catch (Exception | LinkageError e) {
            return java.util.List.of();
        }
        return serverSmithingCache;
    }

    /** 从客户端同步配方中按 display 值等价找锻造 RecipeHolder，或 null。 */
    private static Object findSmithingHolder(
            net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry) {
        if (entry.display() == null) return null;
        try {
            // 优先集成服务器配方（单机全量源）；空（LAN/多机）再走 fabric 同步集。
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> source =
                    serverSmithingHolders();
            if (source.isEmpty()) source = smithingHolders();
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : source) {
                try {
                    for (net.minecraft.world.item.crafting.display.RecipeDisplay display
                            : holder.value().display()) {
                        if (display.equals(entry.display())) {
                            return holder;
                        }
                    }
                } catch (Exception | LinkageError ignored) {
                    // display() unresolvable for this recipe
                }
            }
        } catch (Exception | LinkageError ignored) {
        }
        return null;
    }

    /** 每 tick 轻量轮询（tick 末尾调用）：锻造条目若还有未挂 layout 的
     *  （同步配方/引擎条目时序未对齐时首轮 attach 失败），同步数据就绪后
     *  补挂一次。完成（全部已挂）后为 O(1) 快速返回。 */
    public static void pollSmithingLayoutFallback() {
        try {
            if (!jeiAvailable()) return;
            registerSyncedRecipesListener();
            if (resolveSyncedRecipes() == null) return;
            java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> all =
                    RecipeViewerEngine.allRecipes("minecraft:smithing");
            boolean pending = false;
            for (net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry : all) {
                if (RecipeViewerEngine.getLayout(entry.id()) == null) {
                    pending = true;
                    break;
                }
            }
            if (!pending) return;
            int n = attachSmithingFallbackLayouts(all);
            if (n > 0) {
                BetterRecipeBook.LOGGER.info(
                        "[BRBE-JEI-BRIDGE] fallback attached vanilla smithing layout to {} trim entries", n);
            }
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] pollSmithingLayoutFallback failed: {}", e.toString());
        }
    }

    /** 经 headless 类别（反射）取类别槽位区域尺寸（兜底 layout 用）。 */
    private static int[] categorySize(Identifier typeId) {
        try {
            Object category = indexerCategoryFor.invoke(null, typeId);
            if (category != null) {
                int w = ((Number) category.getClass().getMethod("getWidth").invoke(category)).intValue();
                int h = ((Number) category.getClass().getMethod("getHeight").invoke(category)).intValue();
                return new int[] {w, h};
            }
        } catch (Exception | LinkageError ignored) {
        }
        return new int[] {0, 0};
    }

    /** 把 headless registry 的一个 mod JEI 类型注册为 BRBE 查询类别 tab。 */
    private static void registerPluginCategory(Identifier typeId, List<ItemStack> stations) {
        try {
            String uid = typeId.toString();
            if (BUILTIN_CATEGORY_TYPES.contains(uid)) return;
            String title = (String) titleForMethod.invoke(null, typeId);
            net.minecraft.network.chat.Component titleText = title == null || title.isBlank()
                    ? net.minecraft.network.chat.Component.literal(typeId.getPath())
                    : net.minecraft.network.chat.Component.literal(title);
            com.alonie.brbe.recipeviewer.RecipeViewerCategories.registerExternal(
                    List.of(new com.alonie.brbe.jei.plugins.engine.PluginRecipeViewerCategory(
                            List.of(uid), titleText, stations)));
        } catch (Exception | LinkageError e) {
            // 类别注册失败不阻断数据导入
        }
    }

    private static Object get(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }
}
