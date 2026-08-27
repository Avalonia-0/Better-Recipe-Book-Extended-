package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.21.1 无头 JEI 桥（BRBE 侧，纯反射）：从独立项目 headless-jei mod 的
 * {@code JeiRecipeRegistry}（轻量桥）拉取 JEI 配方条目，转进 BRBE 查询引擎
 * 的 {@link RecipeViewerEngine#registerJeiType}。
 *
 * <p>headless-jei 产物按 intermediary 映射发布（与 BRBE 核心 jar 一致），
 * 其桥 API 无法直接用于 BRBE 的 mojang 编译——改用反射（与 JeiHudHider
 * 同模式）；headless-jei mod 缺席（真实 JEI 或纯原版）时所有调用静默跳过，
 * anvil/grindstone 等类别降级为信息页。</p>
 */
public final class BrbeJeiBridge {

    private BrbeJeiBridge() {}

    private static Class<?> registryClass;
    private static Method typeIdsMethod;
    private static Method entriesForMethod;
    private static Method stationsForMethod;

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
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            registryClass = null;
            return false;
        }
    }

    /** Re-import every JEI registry type into the query engine (called on
     *  JOIN / level load / engine rebuild).  Idempotent. */
    @SuppressWarnings("unchecked")
    public static void refresh() {
        if (!available()) {
            return;
        }
        try {
            List<ResourceLocation> typeIds = (List<ResourceLocation>) typeIdsMethod.invoke(null);
            int total = 0;
            for (ResourceLocation typeId : typeIds) {
                List<com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine.JeiEntry> entries =
                        new ArrayList<>();
                for (Object entry : (List<Object>) entriesForMethod.invoke(null, typeId)) {
                    entries.add(new RecipeViewerEngine.JeiEntry(
                            (ResourceLocation) typeId,
                            get(entry, "recipe"),
                            (List<ItemStack>) get(entry, "inputs"),
                            (List<ItemStack>) get(entry, "outputs")));
                }
                if (entries.isEmpty()) continue;
                List<ItemStack> stations = (List<ItemStack>) stationsForMethod.invoke(null, typeId);
                RecipeViewerEngine.registerJeiType(typeId.toString(), entries,
                        stations == null ? List.of() : stations);
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

    private static Object get(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }
}
