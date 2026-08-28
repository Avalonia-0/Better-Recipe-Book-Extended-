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
    private static Class<?> runtimeBridgeClass;
    private static Method runtimeBridgeRecipeManager;
    private static Class<?> indexerClass;
    private static Method indexerCategoryFor;
    private static Class<?> emptyFocusGroupClass;

    /** Synthetic display id → JEI type uid / raw recipe (渲染委托用)。 */
    private static final Map<RecipeDisplayId, Identifier> UID_BY_ID = new HashMap<>();
    private static final Map<RecipeDisplayId, Object> RECIPE_BY_ID = new HashMap<>();

    /** 本会话是否已导入过（registry 在收集完成前为空，JOIN 时导入会
     *  空转；tick 循环在检测到非空后导入一次，此后跟随引擎重建）。 */
    private static boolean importedOnce;

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

    /** 引擎是否已收到过一次 JEI 条目（tick 轮询用：registry 在 headless
     *  收集完成前为空，JOIN 时导入会空转；导入一次后 registry 的后续
     *  重建也无需重复导入——注册者每次 replace 全量替换）。 */
    public static boolean importedOnce() {
        return importedOnce;
    }

    /** Re-import every JEI registry type into the query engine (JOIN / 引擎重建)。 */
    @SuppressWarnings("unchecked")
    public static void refresh() {
        UID_BY_ID.clear();
        RECIPE_BY_ID.clear();
        if (!jeiAvailable()) {
            return;
        }
        try {
            List<Identifier> typeIds = (List<Identifier>) typeIdsMethod.invoke(null);
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
                    if (layoutW > 0 && layoutH > 0) {
                        RecipeViewerEngine.registerLayout(displayEntry.id(),
                                new RecipeViewerEngine.RecipeLayout(layoutW, layoutH, slotLayouts, null));
                    }
                }
                if (indexed.isEmpty()) continue;
                RecipeViewerEngine.registerType(typeId.toString(), indexed, stations);
                total += indexed.size();
            }
            if (total > 0) {
                importedOnce = true;
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-BRIDGE] imported {} JEI entries from headless-jei ({} types)",
                        total, typeIds.size());
            }
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-BRIDGE] import failed: {}", e.toString());
        }
    }

    private static Object get(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }
}
