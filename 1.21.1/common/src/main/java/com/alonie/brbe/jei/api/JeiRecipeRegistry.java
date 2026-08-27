package com.alonie.brbe.jei.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 无头 JEI 轻量桥：向外部（BRBE 主 mod）暴露嵌入式 JEI 运行时的配方条目。
 *
 * <p>条目由 {@code PluginRecipeIndexer} 在每次收集后写入（mod 插件配方 +
 * 原版 anvil/brewing/grindstone 运行时配方）。外部 mod 在 JOIN/重建时读取
 * {@link #entriesFor} 转进自己的查询引擎；渲染委托 {@link JeiPopupRenderer}。</p>
 *
 * <p>条目是不可变值对象（{@link Entry}），不含任何 mezz.jei 内部引用——
 * 外部只需 {@code com.alonie.headlessjei} 编译依赖即可。</p>
 */
public final class JeiRecipeRegistry {

    private JeiRecipeRegistry() {}

    /** A collected JEI recipe: its JEI type uid, the raw recipe object, its
     *  extracted item inputs/outputs and (for mod recipes) the declared slot
     *  layout. */
    public record Entry(ResourceLocation typeUid, Object recipe,
                        List<ItemStack> inputs, List<ItemStack> outputs,
                        List<Slot> slots, int layoutWidth, int layoutHeight) {
        /** Convenience: entry without slot-layout data (interface-accessed
         *  vanilla JEI recipes). */
        public Entry(ResourceLocation typeUid, Object recipe,
                     List<ItemStack> inputs, List<ItemStack> outputs) {
            this(typeUid, recipe, inputs, outputs, null, 0, 0);
        }

        /** One slot of the recipe's native layout (role = JEI
         *  RecipeIngredientRole ordinal). */
        public record Slot(int x, int y, int role, List<ItemStack> stacks) {}
    }

    private static final Map<ResourceLocation, List<Entry>> ENTRIES = new HashMap<>();
    private static final Map<ResourceLocation, List<ItemStack>> STATIONS = new HashMap<>();

    /** Replace the whole registry (called by the indexer on every rebuild). */
    public static synchronized void replace(Map<ResourceLocation, List<Entry>> entries,
                                            Map<ResourceLocation, List<ItemStack>> stations) {
        ENTRIES.clear();
        STATIONS.clear();
        if (entries != null) ENTRIES.putAll(entries);
        if (stations != null) STATIONS.putAll(stations);
    }

    /** All JEI entries of a type uid (unmodifiable view). */
    public static synchronized List<Entry> entriesFor(ResourceLocation typeUid) {
        List<Entry> entries = ENTRIES.get(typeUid);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    /** All currently registered JEI type uids. */
    public static synchronized List<ResourceLocation> typeIds() {
        return new ArrayList<>(ENTRIES.keySet());
    }

    /** Workstation items of a type uid. */
    public static synchronized List<ItemStack> stationsFor(ResourceLocation typeUid) {
        return STATIONS.getOrDefault(typeUid, List.of());
    }

    /** Whether the registry has any entry for {@code typeUid}. */
    public static synchronized boolean hasType(ResourceLocation typeUid) {
        List<Entry> entries = ENTRIES.get(typeUid);
        return entries != null && !entries.isEmpty();
    }

    /** Drop all entries (disconnect / shutdown). */
    public static synchronized void clear() {
        ENTRIES.clear();
        STATIONS.clear();
    }
}
