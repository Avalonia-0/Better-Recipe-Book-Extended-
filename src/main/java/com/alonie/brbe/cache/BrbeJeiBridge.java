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

    /** Re-import every JEI registry type into the query engine (JOIN / 引擎重建)。 */
    @SuppressWarnings("unchecked")
    public static void refresh() {
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
                // 切石/锻造：条目由 RecipeViewerIndex 从 RecipeManager 构建
                // （有数据、无 layout，且 display 与 headless 重建条目 id 不同）。
                // headless 条目自带 JEI 原生 layout → canRender 需要；为防
                // 重复，RecipeViewerIndex 已跳过这两个类型，这里照常注册。
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
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] import failed: {}", e.toString());
        }
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
     *  UID_BY_ID/RECIPE_BY_ID 供渲染委托（弹窗 → 完整 JEI UI）。 */
    private static void attachVanillaLayouts(Identifier typeId,
                                             List<Object> entries,
                                             List<ItemStack> stations) {
        try {
            int attached = 0;
            java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> all =
                    RecipeViewerEngine.allRecipes(typeId.toString());
            for (Object entry : entries) {
                Object recipe = get(entry, "recipe");
                // headless 收集到的 entry 的 recipe 是 RecipeHolder（datapack），
                // 其 display 与引擎条目（同一 datapack 数据）值等价——按此匹配。
                net.minecraft.world.item.crafting.display.RecipeDisplay targetDisplay = null;
                if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder
                        && holder.value() instanceof net.minecraft.world.item.crafting.display.RecipeDisplay rd) {
                    targetDisplay = rd;
                }
                if (targetDisplay == null) continue;
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
            if (attached > 0) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-BRIDGE] attached vanilla JEI layout to {} stonecutter/smithing entries", attached);
            }
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] attachVanillaLayouts failed: {}", e.toString());
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
